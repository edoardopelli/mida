package it.mida.notifications.api;

import java.time.Instant;

public record NotificationPreferencesDto(
        boolean enabled,
        boolean newAssignmentsEnabled,
        boolean remindersEnabled,
        int reminderDaysBefore,
        String reminderTime,
        String timezone,
        Instant updatedAt) {
}
