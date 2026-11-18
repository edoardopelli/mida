package it.mida.google.classroom.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ClassroomQuery(
        String courseId,
        String type,
        String q,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {
}
