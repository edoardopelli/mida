package it.mida.google.api;

import java.time.Instant;

public record GoogleClientStatusDto(
        boolean configured,
        String clientId,
        String projectId,
        String redirectUri,
        Instant importedAt) {
}
