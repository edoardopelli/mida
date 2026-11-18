package it.mida.google.email.api;

import java.time.Instant;

public record EmailMessageSummaryDto(
        String id,
        String from,
        String subject,
        String snippet,
        Instant receivedAt,
        boolean hasAttachments,
        int attachmentCount) {
}
