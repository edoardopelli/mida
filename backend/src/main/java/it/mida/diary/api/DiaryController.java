package it.mida.diary.api;

import it.mida.common.MidaNotFoundException;
import it.mida.common.PageResponse;
import it.mida.diary.DiaryQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/diary")
@Tag(name = "Diario", description = "Attività svolte registrate da DidUP Family")
public class DiaryController {

    private final DiaryQueryService queryService;

    public DiaryController(DiaryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "Elenca le attività del diario con filtri e paginazione")
    public PageResponse<DiaryEntryView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.search(new DiaryQuery(from, to, subject, teacher, q, page, size));
    }

    @GetMapping("/day")
    @Operation(summary = "Restituisce tutte le attività del diario di un giorno, senza paginazione")
    public List<DiaryEntryView> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) String q) {
        return queryService.day(new DiaryQuery(date, date, subject, teacher, q, 0, 100));
    }

    @GetMapping("/dates")
    @Operation(summary = "Elenca i giorni per cui esiste un diario importato")
    public List<DiaryDateSummary> dates() {
        return queryService.dates();
    }

    @GetMapping("/subjects")
    @Operation(summary = "Elenca le materie presenti nel diario")
    public List<String> subjects() {
        return queryService.subjects();
    }

    @GetMapping("/teachers")
    @Operation(summary = "Elenca i professori presenti nel diario")
    public List<String> teachers() {
        return queryService.teachers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Restituisce una singola attività del diario")
    public DiaryEntryView detail(@PathVariable String id) {
        return queryService.findById(id)
                .orElseThrow(() -> new MidaNotFoundException("Attività del diario non trovata: " + id));
    }
}
