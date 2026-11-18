package it.mida.notifications;

import it.mida.config.MidaProperties;
import it.mida.homework.HomeworkAssignment;
import it.mida.homework.HomeworkAssignmentRepository;
import it.mida.notifications.api.NotificationPreferencesDto;
import it.mida.notifications.api.PushSubscriptionRequest;
import it.mida.notifications.api.TestNotificationResponse;
import it.mida.notifications.api.UpdateNotificationPreferencesRequest;
import it.mida.notifications.api.VapidPublicKeyDto;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final MidaProperties properties;
    private final NotificationPreferencesRepository preferencesRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final PushDeliveryService deliveryService;
    private final VapidKeyService vapidKeyService;

    public NotificationService(
            MidaProperties properties,
            NotificationPreferencesRepository preferencesRepository,
            PushSubscriptionRepository subscriptionRepository,
            HomeworkAssignmentRepository assignmentRepository,
            PushDeliveryService deliveryService,
            VapidKeyService vapidKeyService) {
        this.properties = properties;
        this.preferencesRepository = preferencesRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.assignmentRepository = assignmentRepository;
        this.deliveryService = deliveryService;
        this.vapidKeyService = vapidKeyService;
    }

    public VapidPublicKeyDto publicKeyInfo() {
        String key = vapidKeyService.publicKey();
        return new VapidPublicKeyDto(key, properties.getNotifications().isEnabled(), properties.getNotifications().getVapidSubject());
    }

    public PushSubscription subscribe(PushSubscriptionRequest request, String userAgent) {
        validateEndpoint(request.endpoint());
        validateWebPushKeys(request.p256dh(), request.auth());

        PushSubscription subscription = subscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);
        if (subscription.getId() == null) {
            subscription.setCreatedAt(Instant.now());
        }
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.p256dh());
        subscription.setAuth(request.auth());
        subscription.setUserAgent(truncate(userAgent, 500));
        subscription.setActive(true);
        subscription.setUpdatedAt(Instant.now());
        try {
            return subscriptionRepository.save(subscription);
        } catch (DuplicateKeyException ex) {
            PushSubscription existing = subscriptionRepository.findByEndpoint(request.endpoint())
                    .orElseThrow(() -> ex);
            existing.setP256dh(request.p256dh());
            existing.setAuth(request.auth());
            existing.setActive(true);
            existing.setUpdatedAt(Instant.now());
            return subscriptionRepository.save(existing);
        }
    }

    public void unsubscribe(String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint).ifPresent(subscriptionRepository::delete);
    }

    public NotificationPreferencesDto preferences() {
        return toDto(currentPreferences());
    }

    public NotificationPreferencesDto updatePreferences(UpdateNotificationPreferencesRequest request) {
        try {
            ZoneId.of(request.timezone());
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Fuso orario non valido: " + request.timezone());
        }
        NotificationPreferences preferences = currentPreferences();
        preferences.setEnabled(request.enabled());
        preferences.setNewAssignmentsEnabled(request.newAssignmentsEnabled());
        preferences.setRemindersEnabled(request.remindersEnabled());
        preferences.setReminderDaysBefore(request.reminderDaysBefore());
        preferences.setReminderTime(request.reminderTime());
        preferences.setTimezone(request.timezone());
        preferences.setUpdatedAt(Instant.now());
        return toDto(preferencesRepository.save(preferences));
    }

    public NotificationPreferences currentPreferences() {
        return preferencesRepository.findById(NotificationPreferences.DEFAULT_ID)
                .orElseGet(() -> preferencesRepository.save(NotificationPreferences.defaults()));
    }

    @Async("notificationExecutor")
    public void notifyNewImport(String importId) {
        try {
            if (!properties.getNotifications().isEnabled()) {
                return;
            }
            NotificationPreferences preferences = currentPreferences();
            if (!preferences.isEnabled() || !preferences.isNewAssignmentsEnabled()) {
                return;
            }
            // Solo i compiti creati da questa importazione generano una
            // notifica: gli aggiornamenti di compiti già noti non sono novità.
            List<HomeworkAssignment> created = assignmentRepository.findByFirstImportId(importId);
            if (created.isEmpty()) {
                return;
            }
            List<PushSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
            if (subscriptions.isEmpty()) {
                return;
            }

            Map<String, Long> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (HomeworkAssignment assignment : created) {
                counts.merge(assignment.getSubject(), 1L, Long::sum);
            }
            int total = created.size();
            StringBuilder body = new StringBuilder();
            if (total == 1) {
                body.append("È stato importato 1 compito:");
            } else {
                body.append("Sono stati importati ").append(total).append(" compiti:");
            }
            body.append("\n\n");
            counts.forEach((subject, count) -> body.append(subject).append(": ").append(count).append('\n'));
            String bodyText = body.toString().stripTrailing();

            LocalDate earliestDue = created.stream()
                    .map(HomeworkAssignment::getDueDate)
                    .min(LocalDate::compareTo)
                    .orElse(LocalDate.now());
            String firstAssignmentId = created.stream()
                    .min(java.util.Comparator.comparing(HomeworkAssignment::getDueDate))
                    .map(HomeworkAssignment::getId)
                    .orElse("");
            String url = "/calendario?view=listMonth&date=" + earliestDue
                    + (firstAssignmentId.isBlank() ? "" : "&assignment=" + firstAssignmentId);

            deliveryService.deliver(
                    subscriptions,
                    "new-assignments:" + importId,
                    NotificationDelivery.Type.NEW_ASSIGNMENTS,
                    "MIDA — Nuovi compiti assegnati",
                    bodyText,
                    url,
                    earliestDue,
                    importId,
                    "mida-new");
        } catch (RuntimeException ex) {
            log.warn("Impossibile inviare la notifica per l'import {}: {}", importId, ex.getMessage());
        }
    }

    public TestNotificationResponse sendTest() {
        List<PushSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        PushDeliveryService.DeliverySummary summary = deliveryService.deliver(
                subscriptions,
                "test:" + UUID.randomUUID(),
                NotificationDelivery.Type.TEST,
                "MIDA — Notifica di prova",
                "Le notifiche desktop di MIDA funzionano correttamente.",
                "/",
                LocalDate.now(),
                null,
                "mida-test");
        return new TestNotificationResponse(
                summary.sent(), summary.failed(), summary.expired(), summary.subscriptions());
    }

    public List<PushSubscription> activeSubscriptions() {
        return subscriptionRepository.findByActiveTrue();
    }

    private NotificationPreferencesDto toDto(NotificationPreferences preferences) {
        return new NotificationPreferencesDto(
                preferences.isEnabled(),
                preferences.isNewAssignmentsEnabled(),
                preferences.isRemindersEnabled(),
                preferences.getReminderDaysBefore(),
                preferences.getReminderTime(),
                preferences.getTimezone(),
                preferences.getUpdatedAt());
    }

    private void validateEndpoint(String endpoint) {
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || uri.getHost().equalsIgnoreCase("localhost")) {
                throw new IllegalArgumentException("Endpoint push non valido: è richiesto un URL HTTPS pubblico");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Endpoint push non valido", ex);
        }
    }

    private void validateWebPushKeys(String p256dh, String auth) {
        byte[] publicKey = decodeBase64Url(p256dh);
        byte[] authSecret = decodeBase64Url(auth);
        if (publicKey.length != 65) {
            throw new IllegalArgumentException("Chiave p256dh non valida");
        }
        if (authSecret.length != 16) {
            throw new IllegalArgumentException("Chiave auth non valida");
        }
    }

    private byte[] decodeBase64Url(String value) {
        try {
            String padded = value + "=".repeat((4 - value.length() % 4) % 4);
            return Base64.getUrlDecoder().decode(padded);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Chiave Web Push non valida", ex);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public Map<String, Object> debugInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("activeSubscriptions", subscriptionRepository.countByActiveTrue());
        info.put("vapidAvailable", vapidKeyService.isAvailable());
        return info;
    }
}
