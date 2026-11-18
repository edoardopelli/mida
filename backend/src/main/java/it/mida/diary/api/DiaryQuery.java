package it.mida.diary.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record DiaryQuery(
        LocalDate from,
        LocalDate to,
        String subject,
        String teacher,
        String q,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {

    public DiaryQuery {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La data iniziale non può essere successiva alla data finale");
        }
    }
}
