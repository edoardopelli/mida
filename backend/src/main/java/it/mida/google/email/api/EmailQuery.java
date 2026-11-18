package it.mida.google.email.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record EmailQuery(
        String from,
        String q,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {
}
