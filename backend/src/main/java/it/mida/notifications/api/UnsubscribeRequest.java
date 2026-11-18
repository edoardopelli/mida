package it.mida.notifications.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnsubscribeRequest(@NotBlank @Size(max = 2000) String endpoint) {
}
