package it.mida.notifications.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNotificationPreferencesRequest(
        @NotNull Boolean enabled,
        @NotNull Boolean newAssignmentsEnabled,
        @NotNull Boolean remindersEnabled,
        @NotNull @Min(0) @Max(14) Integer reminderDaysBefore,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "L'orario deve essere nel formato HH:mm") String reminderTime,
        @NotBlank @Size(max = 64) String timezone) {
}
