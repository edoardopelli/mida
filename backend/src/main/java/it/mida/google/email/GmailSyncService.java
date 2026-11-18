package it.mida.google.email;

import tools.jackson.databind.JsonNode;
import it.mida.attachments.AttachmentStorageService;
import it.mida.config.MidaProperties;
import it.mida.events.MidaEventPublisher;
import it.mida.events.MidaEvents;
import it.mida.google.GoogleApiClient;
import it.mida.google.GoogleApiException;
import it.mida.google.GoogleConnectionService;
import it.mida.google.GoogleNotConfiguredException;
import it.mida.google.GoogleReauthRequiredException;
import it.mida.google.GoogleSettings;
import it.mida.google.GoogleSyncService;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sincronizza Gmail tramite API OAuth, conserva email e allegati e importa i
 * TXT DidUP. Quando arrivano nuove email avvia anche Classroom.
 */
@Service
public class GmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);
    private static final int MAX_ERROR_LENGTH = 800;

    private final GoogleApiClient api;
    private final GoogleConnectionService connectionService;
    private final GmailMessageParser parser;
    private final GmailSyncStateRepository stateRepository;
    private final EmailMessageRepository messageRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final AttachmentStorageService fileService;
    private final DidupAttachmentImporter attachmentImporter;
    private final GoogleSyncService googleSyncService;
    private final MidaProperties properties;
    private final MidaEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GmailSyncService(
            GoogleApiClient api,
            GoogleConnectionService connectionService,
            GmailMessageParser parser,
            GmailSyncStateRepository stateRepository,
            EmailMessageRepository messageRepository,
            EmailAttachmentRepository attachmentRepository,
            AttachmentStorageService fileService,
            DidupAttachmentImporter attachmentImporter,
            GoogleSyncService googleSyncService,
            MidaProperties properties,
            MidaEventPublisher events) {
        this.api = api;
        this.connectionService = connectionService;
        this.parser = parser;
        this.stateRepository = stateRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.fileService = fileService;
        this.attachmentImporter = attachmentImporter;
        this.googleSyncService = googleSyncService;
        this.properties = properties;
        this.events = events;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Async
    public void syncAsync() {
        sync();
    }

    @Scheduled(
            initialDelayString = "${mida.google.gmail-initial-delay-ms:30000}",
            fixedDelayString = "${mida.google.gmail-sync-interval-ms:300000}")
    public void scheduledSync() {
        sync();
    }

    public void sync() {
        if (!connectionService.configured() || connectionService.connection().isEmpty()) {
            return;
        }
        GoogleSettings settings = connectionService.settings();
        if (!settings.isGmailEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        GmailSyncState state = state();
        state.setLastSyncStatus("SYNCING");
        state.setLastError(null);
        stateRepository.save(state);

        int seen = 0;
        int created = 0;
        int attachments = 0;
        int newAttachments = 0;
        int errors = 0;
        String lastError = null;

        try {
            String accessToken = connectionService.accessToken();
            Map<String, String> query = new LinkedHashMap<>();
            query.put("q", settings.getGmailQuery());
            query.put("maxResults", Integer.toString(properties.getGoogle().getGmailMaxResults()));
            query.put("includeSpamTrash", "false");
            JsonNode response = api.getJson(
                    GoogleApiClient.withQuery(
                            GoogleApiClient.GMAIL_BASE + "/messages", query),
                    accessToken);
            JsonNode messages = response.path("messages");
            if (messages.isArray()) {
                for (JsonNode reference : messages) {
                    String gmailId = reference.path("id").asText();
                    if (!StringUtils.hasText(gmailId)) {
                        continue;
                    }
                    seen++;
                    try {
                        Optional<EmailMessage> existing =
                                messageRepository.findByProviderMessageId(gmailId);
                        if (existing.isPresent() && existing.get().isAttachmentsComplete()) {
                            continue;
                        }
                        JsonNode full = api.getJson(
                                GoogleApiClient.GMAIL_BASE + "/messages/" + gmailId
                                        + "?format=full",
                                accessToken);
                        GmailMessageParser.ParsedGmailMessage parsed = parser.parse(full);
                        EmailMessage email = existing.orElseGet(EmailMessage::new);
                        boolean isNew = email.getId() == null;
                        if (isNew) {
                            email.setId(UUID.randomUUID().toString());
                            email.setFirstSeenAt(Instant.now());
                            created++;
                        }
                        email.setProviderMessageId(gmailId);
                        email.setProvider("GMAIL");
                        email.setMailbox("GMAIL");
                        email.setThreadId(parsed.threadId());
                        email.setFrom(parsed.from());
                        email.setTo(parsed.to());
                        email.setCc(parsed.cc());
                        email.setSubject(parsed.subject());
                        email.setSnippet(parsed.snippet());
                        email.setBodyText(parsed.bodyText());
                        email.setReceivedAt(parsed.receivedAt());
                        email.setLabels(parsed.labels());
                        email.setLastSeenAt(Instant.now());
                        email.setAttachmentsComplete(false);
                        email = messageRepository.save(email);

                        boolean complete = true;
                        int index = 0;
                        for (GmailMessageParser.ParsedAttachment parsedAttachment
                                : parsed.attachments()) {
                            try {
                                AttachmentResult result = storeAttachment(
                                        email, parsedAttachment, index, accessToken);
                                if (result.created()) {
                                    newAttachments++;
                                }
                            } catch (GoogleReauthRequiredException ex) {
                                throw ex;
                            } catch (RuntimeException ex) {
                                complete = false;
                                errors++;
                                lastError = "Allegato di " + email.getSubject() + ": "
                                        + ex.getMessage();
                                log.warn("Allegato Gmail non archiviato per {}: {}",
                                        email.getSubject(), ex.getMessage());
                            }
                            index++;
                        }

                        int total = (int) attachmentRepository
                                .countByEmailMessageId(email.getId());
                        email.setAttachmentCount(total);
                        email.setHasAttachments(total > 0);
                        email.setAttachmentsComplete(complete);
                        messageRepository.save(email);
                        attachments += total;
                    } catch (GoogleReauthRequiredException ex) {
                        throw ex;
                    } catch (RuntimeException ex) {
                        errors++;
                        lastError = ex.getMessage();
                        log.warn("Messaggio Gmail {} non sincronizzato: {}", gmailId,
                                ex.getMessage());
                    }
                }
            }

            state.setLastSyncAt(Instant.now());
            state.setLastSyncStatus(errors == 0 ? "OK" : "ERROR");
            state.setLastError(truncate(lastError));
            state.setLastFetchedCount(seen);
            state.setLastNewCount(created);
            state.setLastAttachmentCount(attachments);
            state.setLastNewAttachmentCount(newAttachments);
            stateRepository.save(state);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fetched", seen);
            payload.put("newEmails", created);
            payload.put("attachments", attachments);
            payload.put("newAttachments", newAttachments);
            payload.put("errors", errors);
            events.publish(MidaEvents.MAIL_SYNC, payload);

            log.info("Sincronizzazione Gmail completata: {} messaggi, {} nuove email, {} allegati, {} errori",
                    seen, created, newAttachments, errors);

            if (created > 0) {
                // Classroom viene interrogata solo quando arriva una nuova email.
                googleSyncService.syncAsync();
            }
        } catch (GoogleReauthRequiredException ex) {
            errors++;
            lastError = ex.getMessage();
            markError(state, lastError);
            log.warn("Sincronizzazione Gmail sospesa: {}", ex.getMessage());
        } catch (GoogleNotConfiguredException ex) {
            errors++;
            lastError = ex.getMessage();
            markError(state, lastError);
        } catch (RuntimeException ex) {
            errors++;
            lastError = ex.getMessage();
            markError(state, lastError);
            log.error("Sincronizzazione Gmail non riuscita", ex);
        } finally {
            running.set(false);
        }
    }

    private AttachmentResult storeAttachment(
            EmailMessage email,
            GmailMessageParser.ParsedAttachment parsed,
            int index,
            String accessToken) {
        String providerId = StringUtils.hasText(parsed.providerAttachmentId())
                ? parsed.providerAttachmentId()
                : "inline-" + index;
        Optional<EmailAttachment> existing = attachmentRepository
                .findByEmailMessageIdAndProviderAttachmentId(email.getId(), providerId);
        if (existing.isPresent() && StringUtils.hasText(existing.get().getGridFsId())) {
            return new AttachmentResult(false);
        }

        EmailAttachment attachment = existing.orElseGet(EmailAttachment::new);
        if (attachment.getId() == null) {
            attachment.setId(UUID.randomUUID().toString());
            attachment.setEmailMessageId(email.getId());
            attachment.setProviderAttachmentId(providerId);
            attachment.setCreatedAt(Instant.now());
        }
        attachment.setFilename(parsed.filename());
        attachment.setMimeType(parsed.mimeType());
        attachment.setInline(parsed.inline());
        attachment.setContentId(parsed.contentId());
        attachment.setSize(parsed.size());

        long maxSize = properties.getGoogle().getMaxAttachmentSizeBytes();
        if (parsed.data() == null && parsed.size() > maxSize) {
            attachment.setImportStatus(EmailAttachment.IMPORT_TOO_LARGE);
            attachment.setImportMessage("Allegato più grande del limite configurato");
            attachmentRepository.save(attachment);
            return new AttachmentResult(false);
        }

        byte[] bytes = parsed.data();
        if (bytes == null && StringUtils.hasText(parsed.providerAttachmentId())) {
            JsonNode response = api.getJson(
                    GoogleApiClient.GMAIL_BASE + "/messages/" + email.getProviderMessageId()
                            + "/attachments/" + parsed.providerAttachmentId(),
                    accessToken);
            String data = response.path("data").asText("");
            if (!StringUtils.hasText(data)) {
                throw new GoogleApiException("Allegato Gmail privo di contenuto");
            }
            bytes = decode(data);
        }
        if (bytes == null) {
            throw new GoogleApiException("Allegato Gmail privo di contenuto");
        }
        attachment.setSize(bytes.length);

        if (bytes.length > maxSize) {
            attachment.setImportStatus(EmailAttachment.IMPORT_TOO_LARGE);
            attachment.setImportMessage("Allegato più grande del limite configurato");
            attachmentRepository.save(attachment);
            return new AttachmentResult(false);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "GMAIL");
        metadata.put("emailMessageId", email.getId());
        metadata.put("providerAttachmentId", providerId);
        AttachmentStorageService.StoredFile stored = fileService.store(
                bytes, parsed.filename(), parsed.mimeType(), metadata);
        attachment.setGridFsId(stored.id());
        attachment.setSha256(stored.sha256());
        attachmentImporter.apply(attachment, bytes, parsed.filename());
        attachmentRepository.save(attachment);
        return new AttachmentResult(true);
    }

    private GmailSyncState state() {
        return stateRepository.findById(GmailSyncState.DEFAULT_ID)
                .orElseGet(GmailSyncState::new);
    }

    private void markError(GmailSyncState state, String message) {
        state.setLastSyncAt(Instant.now());
        state.setLastSyncStatus("ERROR");
        state.setLastError(truncate(message));
        stateRepository.save(state);
    }

    private byte[] decode(String value) {
        String normalized = value.replaceAll("\\s", "");
        int padding = (4 - normalized.length() % 4) % 4;
        normalized = normalized + "=".repeat(padding);
        return Base64.getUrlDecoder().decode(normalized);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH) + "…";
    }

    private record AttachmentResult(boolean created) {
    }
}
