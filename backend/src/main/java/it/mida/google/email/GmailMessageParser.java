package it.mida.google.email;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import tools.jackson.databind.JsonNode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Estrae da un messaggio Gmail (formato {@code full}) intestazioni, corpo testo
 * e allegati, inclusi quelli annidati in parti MIME multipart.
 */
@Component
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class GmailMessageParser {

    private static final Pattern CHARSET_PATTERN =
            Pattern.compile("charset\\s*=\\s*\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    public ParsedGmailMessage parse(JsonNode message) {
        String gmailId = message.path("id").asText();
        String internalDate = message.path("internalDate").asText("");
        long internalDateMillis = parseLong(internalDate);
        String threadId = message.path("threadId").asText(null);
        String snippet = message.path("snippet").asText("");
        JsonNode payload = message.path("payload");

        String from = MimeHeaderDecoder.decode(header(payload, "From"));
        String to = MimeHeaderDecoder.decode(header(payload, "To"));
        String cc = MimeHeaderDecoder.decode(header(payload, "Cc"));
        String subject = MimeHeaderDecoder.decode(header(payload, "Subject"));

        Instant receivedAt = internalDateMillis > 0
                ? Instant.ofEpochMilli(internalDateMillis)
                : parseDate(header(payload, "Date"));

        List<String> labels = new ArrayList<>();
        JsonNode labelIds = message.path("labelIds");
        if (labelIds.isArray()) {
            labelIds.forEach(label -> labels.add(label.asText()));
        }

        Collector collector = new Collector();
        walk(payload, collector);

        String body = join(collector.text);
        if (body.isBlank()) {
            body = HtmlText.toText(join(collector.html));
        }
        if (body.isBlank()) {
            body = snippet;
        }

        return new ParsedGmailMessage(
                gmailId,
                threadId,
                from,
                to,
                cc,
                subject,
                snippet,
                body,
                receivedAt,
                List.copyOf(labels),
                List.copyOf(collector.attachments));
    }

    private void walk(JsonNode part, Collector collector) {
        if (part == null || part.isMissingNode()) {
            return;
        }
        String mimeType = part.path("mimeType").asText("");
        String filename = MimeHeaderDecoder.decode(part.path("filename").asText(""));
        String contentId = header(part, "Content-ID");
        String disposition = header(part, "Content-Disposition").toLowerCase(Locale.ROOT);
        boolean inline = !contentId.isBlank() && disposition.startsWith("inline");

        JsonNode body = part.path("body");
        String data = body.hasNonNull("data") ? body.path("data").asText() : null;
        String attachmentId = body.hasNonNull("attachmentId")
                ? body.path("attachmentId").asText()
                : null;
        long declaredSize = body.path("size").asLong(0);

        boolean attachment = !filename.isBlank() && (attachmentId != null || data != null);
        if (attachment) {
            byte[] decoded = data == null ? null : decode(data);
            collector.attachments.add(new ParsedAttachment(
                    attachmentId,
                    filename.isBlank() ? "allegato-" + (collector.attachments.size() + 1) : filename,
                    mimeType.isBlank() ? "application/octet-stream" : mimeType,
                    decoded == null ? declaredSize : decoded.length,
                    inline,
                    contentId,
                    decoded));
        } else if (data != null) {
            String decoded = decodeToString(data, charset(part));
            if ("text/plain".equalsIgnoreCase(mimeType)) {
                collector.text.add(decoded);
            } else if ("text/html".equalsIgnoreCase(mimeType)) {
                collector.html.add(decoded);
            }
        }

        JsonNode parts = part.path("parts");
        if (parts.isArray()) {
            parts.forEach(child -> walk(child, collector));
        }
    }

    private String header(JsonNode payload, String name) {
        JsonNode headers = payload.path("headers");
        if (!headers.isArray()) {
            return "";
        }
        for (JsonNode header : headers) {
            if (name.equalsIgnoreCase(header.path("name").asText())) {
                return header.path("value").asText("");
            }
        }
        return "";
    }

    private String charset(JsonNode part) {
        String contentType = header(part, "Content-Type");
        Matcher matcher = CHARSET_PATTERN.matcher(contentType);
        return matcher.find() ? matcher.group(1) : "UTF-8";
    }

    private String decodeToString(String data, String charset) {
        Charset target;
        try {
            target = Charset.forName(charset);
        } catch (RuntimeException ex) {
            target = StandardCharsets.UTF_8;
        }
        return new String(decode(data), target);
    }

    private byte[] decode(String value) {
        String normalized = value.replaceAll("\\s", "");
        int padding = (4 - normalized.length() % 4) % 4;
        normalized = normalized + "=".repeat(padding);
        return Base64.getUrlDecoder().decode(normalized);
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException ex) {
            return Instant.now();
        }
    }

    private String join(List<String> parts) {
        return parts.stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .reduce((first, second) -> first + "\n\n" + second)
                .orElse("");
    }

    private static final class Collector {
        private final List<String> text = new ArrayList<>();
        private final List<String> html = new ArrayList<>();
        private final List<ParsedAttachment> attachments = new ArrayList<>();
    }

    public record ParsedGmailMessage(
            String gmailId,
            String threadId,
            String from,
            String to,
            String cc,
            String subject,
            String snippet,
            String bodyText,
            Instant receivedAt,
            List<String> labels,
            List<ParsedAttachment> attachments) {
    }

    public record ParsedAttachment(
            String providerAttachmentId,
            String filename,
            String mimeType,
            long size,
            boolean inline,
            String contentId,
            byte[] data) {
    }
}
