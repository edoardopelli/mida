package it.mida.google.api;

import jakarta.validation.constraints.NotBlank;

public record ImportGoogleClientRequest(@NotBlank String json) {
}
