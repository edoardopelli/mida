package it.mida.notifications.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 2000) String endpoint,
        @NotBlank @Size(max = 255) String p256dh,
        @NotBlank @Size(max = 255) String auth) {
}
