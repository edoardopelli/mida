package it.mida.google.api;

import java.time.Instant;
import java.util.List;

public record GoogleStatusDto(
        boolean configured,
        boolean connected,
        String accountEmail,
        Instant connectedAt,
        List<String> scopes,
        Instant lastSyncAt,
        String lastSyncStatus,
        String lastError,
        boolean syncInProgress,
        boolean classroomEnabled,
        int lastClassroomItemCount,
        int lastNewClassroomItemCount,
        Instant settingsUpdatedAt) {
}
