package it.mida.google.api;

import jakarta.validation.constraints.Size;

public record UpdateGoogleSettingsRequest(
        @Size(max = 500) String gmailQuery,
        Boolean gmailEnabled,
        Boolean classroomEnabled) {
}
