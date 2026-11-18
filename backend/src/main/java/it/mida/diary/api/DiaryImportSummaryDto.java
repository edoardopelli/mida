package it.mida.diary.api;

import java.time.Instant;

public record DiaryImportSummaryDto(
        String id,
        String sourceCode,
        String originalFilename,
        Integer filenameSuffix,
        String sha256,
        Instant importedAt,
        int entryCount,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int schoolYearStart) {
}
