package it.mida.homework.api;

import java.time.Instant;
import java.time.LocalDate;

public record HomeworkView(
        String id,
        String importId,
        String sourceCode,
        String originalFilename,
        Instant importedAt,
        LocalDate assignedDate,
        String assignedDateRaw,
        LocalDate dueDate,
        String dueDateRaw,
        String subject,
        String teacher,
        String description,
        boolean completed,
        Instant completedAt,
        int revision,
        Instant updatedAt) {
}
