package it.mida.google.email.api;

import java.time.Instant;
import java.util.List;

public record EmailMessageView(
        String id,
        String from,
        String to,
        String cc,
        String subject,
        String bodyText,
        Instant receivedAt,
        List<String> labels,
        List<EmailAttachmentDto> attachments) {
}
