package it.mida.homework.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public record HomeworkQuery(
        LocalDate from,
        LocalDate to,
        DateField dateField,
        String subject,
        String teacher,
        Boolean completed,
        String q,
        @Min(0) int page,
        @Min(1) @Max(100) int size) {

    public enum DateField {
        DUE,
        ASSIGNED
    }

    public HomeworkQuery {
        if (dateField == null) {
            dateField = DateField.DUE;
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La data iniziale non può essere successiva alla data finale");
        }
    }

    public String datePath() {
        return dateField == DateField.ASSIGNED ? "assignedDate" : "dueDate";
    }
}
