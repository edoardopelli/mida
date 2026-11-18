package it.mida.common;

import java.time.Instant;

/**
 * Metadati comuni a un'importazione, usati per collegare compiti e attività
 * del diario al file che li ha creati o modificati.
 */
public record ImportReference(
        String importId, String sourceCode, String originalFilename, Instant importedAt) {
}
