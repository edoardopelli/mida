package it.mida.diary.api;

import java.time.LocalDate;
import java.util.List;

public record DiaryDateSummary(LocalDate date, long entryCount, List<String> subjects) {
}
