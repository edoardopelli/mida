package it.mida.google.classroom.api;

import java.time.Instant;
import java.time.LocalDate;

public record ClassroomItemSummaryDto(
        String id,
        String courseId,
        String courseName,
        String courseSection,
        String itemType,
        String title,
        String description,
        LocalDate dueDate,
        String dueTime,
        Instant updateTime,
        int materialCount,
        String alternateLink) {
}
