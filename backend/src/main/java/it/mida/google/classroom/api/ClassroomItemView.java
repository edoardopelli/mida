package it.mida.google.classroom.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClassroomItemView(
        String id,
        String courseId,
        String courseName,
        String courseSection,
        String itemType,
        String title,
        String description,
        String state,
        String alternateLink,
        LocalDate dueDate,
        String dueTime,
        Instant creationTime,
        Instant updateTime,
        List<ClassroomMaterialDto> materials) {
}
