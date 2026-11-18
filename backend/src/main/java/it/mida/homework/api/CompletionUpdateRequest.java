package it.mida.homework.api;

import jakarta.validation.constraints.NotNull;

public record CompletionUpdateRequest(@NotNull(message = "Il campo completed è obbligatorio") Boolean completed) {
}
