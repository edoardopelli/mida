package it.mida.diary.api;

import java.time.Instant;
import java.time.LocalDate;

public record DiaryEntryView(
        String id,
        String importId,
        String sourceCode,
        String originalFilename,
        Instant importedAt,
        LocalDate activityDate,
        String activityDateRaw,
        int period,
        String periodLabel,
        String subject,
        String teacher,
        String description,
        int revision,
        Instant updatedAt) {
}
