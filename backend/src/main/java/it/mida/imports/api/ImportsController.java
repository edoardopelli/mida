package it.mida.imports.api;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.common.PageResponse;
import it.mida.diary.DiaryImport;
import it.mida.diary.DiaryImportRepository;
import it.mida.diary.api.DiaryImportSummaryDto;
import it.mida.homework.HomeworkImport;
import it.mida.homework.HomeworkImportRepository;
import it.mida.imports.ImportStatusService;
import it.mida.issues.ImportIssue;
import it.mida.issues.ImportIssueRepository;
import it.mida.issues.ImportIssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/imports")
@Tag(name = "Importazioni", description = "Storico, anomalie e stato del monitoraggio")
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class ImportsController {

    private final HomeworkImportRepository importRepository;
    private final DiaryImportRepository diaryImportRepository;
    private final ImportIssueRepository issueRepository;
    private final ImportStatusService statusService;

    public ImportsController(
            HomeworkImportRepository importRepository,
            DiaryImportRepository diaryImportRepository,
            ImportIssueRepository issueRepository,
            ImportStatusService statusService) {
        this.importRepository = importRepository;
        this.diaryImportRepository = diaryImportRepository;
        this.issueRepository = issueRepository;
        this.statusService = statusService;
    }

    @GetMapping
    @Operation(summary = "Elenca le importazioni, dalla più recente")
    public PageResponse<ImportSummaryDto> imports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<HomeworkImport> result = importRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "importedAt")));
        return PageResponse.of(
                result.getContent().stream().map(this::toDto).toList(),
                page,
                size,
                result.getTotalElements());
    }

    @GetMapping("/diary")
    @Operation(summary = "Elenca le importazioni del diario, dalla più recente")
    public PageResponse<DiaryImportSummaryDto> diaryImports(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<DiaryImport> result = diaryImportRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "importedAt")));
        return PageResponse.of(
                result.getContent().stream().map(this::toDiaryDto).toList(),
                page,
                size,
                result.getTotalElements());
    }

    @GetMapping("/issues")
    @Operation(summary = "Elenca le anomalie di importazione")
    public PageResponse<ImportIssueService.ImportIssueDto> issues(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        Page<ImportIssue> result;
        if (status == null || status.isBlank() || status.equalsIgnoreCase("ALL")) {
            result = issueRepository.findAll(pageRequest);
        } else {
            ImportIssue.Status issueStatus;
            try {
                issueStatus = ImportIssue.Status.valueOf(status.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Stato anomalia non valido: " + status);
            }
            result = issueRepository.findByStatusOrderByLastSeenAtDesc(issueStatus, pageRequest);
        }
        return PageResponse.of(
                result.getContent().stream().map(ImportIssueService::toDto).toList(),
                page,
                size,
                result.getTotalElements());
    }

    @GetMapping("/status")
    @Operation(summary = "Stato corrente del monitoraggio Downloads e del database")
    public ImportStatusService.ImportStatusDto status() {
        return statusService.status();
    }

    private ImportSummaryDto toDto(HomeworkImport imported) {
        return new ImportSummaryDto(
                imported.getId(),
                imported.getSourceCode(),
                imported.getOriginalFilename(),
                imported.getFilenameSuffix(),
                imported.getSha256(),
                imported.getImportedAt(),
                imported.getAssignmentCount(),
                imported.getCreatedCount(),
                imported.getUpdatedCount(),
                imported.getUnchangedCount(),
                imported.getSchoolYearStart());
    }

    private DiaryImportSummaryDto toDiaryDto(DiaryImport imported) {
        return new DiaryImportSummaryDto(
                imported.getId(),
                imported.getSourceCode(),
                imported.getOriginalFilename(),
                imported.getFilenameSuffix(),
                imported.getSha256(),
                imported.getImportedAt(),
                imported.getEntryCount(),
                imported.getCreatedCount(),
                imported.getUpdatedCount(),
                imported.getUnchangedCount(),
                imported.getSchoolYearStart());
    }
}
