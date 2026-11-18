package it.mida.google.email.api;

import java.time.Instant;

public record GmailStatusDto(
        boolean configured,
        boolean connected,
        boolean gmailEnabled,
        String gmailQuery,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastError,
        boolean syncInProgress,
        int lastFetchedCount,
        int lastNewCount,
        int lastAttachmentCount,
        int lastNewAttachmentCount) {
}
