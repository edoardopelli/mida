package it.mida.imports.api;

import java.time.Instant;

public record ImportSummaryDto(
        String id,
        String sourceCode,
        String originalFilename,
        Integer filenameSuffix,
        String sha256,
        Instant importedAt,
        int assignmentCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int schoolYearStart) {
}
