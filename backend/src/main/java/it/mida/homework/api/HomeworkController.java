package it.mida.homework.api;

import it.mida.common.PageResponse;
import it.mida.homework.HomeworkCommandService;
import it.mida.homework.HomeworkQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/homework")
@Tag(name = "Compiti", description = "Consultazione e aggiornamento dei compiti")
public class HomeworkController {

    private final HomeworkQueryService queryService;
    private final HomeworkCommandService commandService;

    public HomeworkController(HomeworkQueryService queryService, HomeworkCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @GetMapping
    @Operation(summary = "Elenca i compiti con filtri e paginazione")
    public PageResponse<HomeworkView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) HomeworkQuery.DateField dateField,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queryService.search(
                new HomeworkQuery(from, to, dateField, subject, teacher, completed, q, page, size));
    }

    @GetMapping("/calendar")
    @Operation(summary = "Restituisce tutti i compiti dell'intervallo richiesto, senza paginazione")
    public List<HomeworkView> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) HomeworkQuery.DateField dateField,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String teacher,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) String q) {
        return queryService.calendar(
                new HomeworkQuery(from, to, dateField, subject, teacher, completed, q, 0, 100));
    }

    @GetMapping("/subjects")
    @Operation(summary = "Elenca le materie presenti")
    public List<String> subjects() {
        return queryService.subjects();
    }

    @GetMapping("/teachers")
    @Operation(summary = "Elenca i professori presenti")
    public List<String> teachers() {
        return queryService.teachers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Restituisce un singolo compito")
    public HomeworkView detail(@PathVariable String id) {
        return queryService.findById(id)
                .orElseThrow(() -> new it.mida.common.MidaNotFoundException("Compito non trovato: " + id));
    }

    @PatchMapping("/{id}/completion")
    @Operation(summary = "Segna un compito come completato o da fare")
    public HomeworkView updateCompletion(
            @PathVariable String id,
            @Valid @RequestBody CompletionUpdateRequest request) {
        return commandService.setCompleted(id, request.completed());
    }
}
