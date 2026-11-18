package it.mida.imports;

import it.mida.common.ImportReference;
import it.mida.config.MidaProperties;
import it.mida.diary.DiaryEntry;
import it.mida.diary.DiaryEntryWriter;
import it.mida.diary.DiaryImport;
import it.mida.diary.DiaryImportRepository;
import it.mida.events.MidaEventPublisher;
import it.mida.events.MidaEvents;
import it.mida.homework.HomeworkAssignment;
import it.mida.homework.HomeworkAssignmentRepository;
import it.mida.homework.HomeworkAssignmentWriter;
import it.mida.homework.HomeworkImport;
import it.mida.homework.HomeworkImportRepository;
import it.mida.issues.ImportIssue;
import it.mida.issues.ImportIssueService;
import it.mida.notifications.NotificationService;
import it.mida.parser.DidupContentDetector;
import it.mida.parser.DidupDiaryParser;
import it.mida.parser.DidupFileKind;
import it.mida.parser.DidupFileParser;
import it.mida.parser.DidupFileName;
import it.mida.parser.DidupParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Importa un file DidUP TXT, di compiti o di diario. Ogni compito e ogni ora
 * del diario sono scritti come documenti autonomi e riconosciuti tra
 * esportazioni successive. Il file originale viene eliminato solo dopo che
 * tutte le scritture sono state confermate.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final Set<ImportIssue.Type> RETRY_SUPPRESSING_ISSUES = Set.of(
            ImportIssue.Type.PARSE_ERROR,
            ImportIssue.Type.VALIDATION_ERROR,
            ImportIssue.Type.READ_ERROR,
            ImportIssue.Type.FILE_TOO_LARGE);

    private final MidaProperties properties;
    private final DidupContentDetector detector;
    private final DidupFileParser homeworkParser;
    private final DidupDiaryParser diaryParser;
    private final HomeworkImportRepository importRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final HomeworkAssignmentWriter assignmentWriter;
    private final DiaryImportRepository diaryImportRepository;
    private final DiaryEntryWriter diaryEntryWriter;
    private final ImportIssueService issueService;
    private final NotificationService notificationService;
    private final MidaEventPublisher events;

    public ImportService(
            MidaProperties properties,
            DidupContentDetector detector,
            DidupFileParser homeworkParser,
            DidupDiaryParser diaryParser,
            HomeworkImportRepository importRepository,
            HomeworkAssignmentRepository assignmentRepository,
            HomeworkAssignmentWriter assignmentWriter,
            DiaryImportRepository diaryImportRepository,
            DiaryEntryWriter diaryEntryWriter,
            ImportIssueService issueService,
            NotificationService notificationService,
            MidaEventPublisher events) {
        this.properties = properties;
        this.detector = detector;
        this.homeworkParser = homeworkParser;
        this.diaryParser = diaryParser;
        this.importRepository = importRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentWriter = assignmentWriter;
        this.diaryImportRepository = diaryImportRepository;
        this.diaryEntryWriter = diaryEntryWriter;
        this.issueService = issueService;
        this.notificationService = notificationService;
        this.events = events;
    }

    public ImportOutcome importFile(Path file, DidupFileName fileName) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();

        if (!isDirectChildOfConfiguredDirectory(file)) {
            log.warn("File ignorato perché esterno alla directory configurata: {}", file);
            return ImportOutcome.error(sourceCode, filename, "File esterno alla directory configurata");
        }

        byte[] content;
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
                return ImportOutcome.error(sourceCode, filename, "Il file non è un file regolare");
            }
            if (attributes.size() > properties.getImports().getMaxFileSizeBytes()) {
                issueService.recordOnce(
                        ImportIssue.Type.FILE_TOO_LARGE,
                        filename,
                        sourceCode,
                        null,
                        "Il file supera il limite di " + properties.getImports().getMaxFileSizeBytes() + " byte");
                return ImportOutcome.error(sourceCode, filename, "File troppo grande");
            }
            content = Files.readAllBytes(file);
        } catch (IOException | SecurityException ex) {
            issueService.record(ImportIssue.Type.READ_ERROR, filename, sourceCode, null,
                    "Impossibile leggere il file: " + ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Errore di lettura: " + ex.getMessage());
        }

        if (content.length > properties.getImports().getMaxFileSizeBytes()) {
            issueService.recordOnce(ImportIssue.Type.FILE_TOO_LARGE, filename, sourceCode, null,
                    "Il file supera il limite di " + properties.getImports().getMaxFileSizeBytes() + " byte");
            return ImportOutcome.error(sourceCode, filename, "File troppo grande");
        }

        String sha256 = sha256(content);
        return processContent(content, fileName, sha256, file);
    }

    /**
     * Importa un contenuto già in memoria, senza file temporaneo. È usato per
     * gli allegati DidUP scaricati da Gmail: nessun file viene eliminato e la
     * deduplica per codice e SHA-256 resta identica al flusso da cartella.
     */
    public ImportOutcome importBytes(byte[] content, DidupFileName fileName) {
        if (content == null) {
            return ImportOutcome.error(fileName.sourceCode(), fileName.originalFilename(),
                    "Contenuto vuoto");
        }
        if (content.length > properties.getImports().getMaxFileSizeBytes()) {
            issueService.recordOnce(
                    ImportIssue.Type.FILE_TOO_LARGE,
                    fileName.originalFilename(),
                    fileName.sourceCode(),
                    null,
                    "Il contenuto supera il limite di "
                            + properties.getImports().getMaxFileSizeBytes() + " byte");
            return ImportOutcome.error(fileName.sourceCode(), fileName.originalFilename(),
                    "Contenuto troppo grande");
        }
        String sha256 = sha256(content);
        return processContent(content, fileName, sha256, null);
    }

    private ImportOutcome processContent(
            byte[] content, DidupFileName fileName, String sha256, Path file) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();

        if (issueService.hasOpenIssue(filename, sha256, RETRY_SUPPRESSING_ISSUES)) {
            return ImportOutcome.skipped(sourceCode, filename,
                    "Il contenuto invariato ha già prodotto un errore; verrà ritentato solo se cambia");
        }

        Optional<HomeworkImport> existingHomework = importRepository.findBySourceCode(sourceCode);
        Optional<DiaryImport> existingDiary = diaryImportRepository.findBySourceCode(sourceCode);
        if (existingHomework.isPresent() || existingDiary.isPresent()) {
            return handleExisting(existingHomework, existingDiary, file, fileName, sha256);
        }

        Optional<HomeworkImport> sameHomeworkContent = importRepository.findFirstBySha256(sha256);
        Optional<DiaryImport> sameDiaryContent = diaryImportRepository.findFirstBySha256(sha256);
        if (sameHomeworkContent.isPresent() || sameDiaryContent.isPresent()) {
            return handleSameContent(sameHomeworkContent, sameDiaryContent, file, fileName, sha256);
        }

        DidupFileKind kind;
        try {
            kind = detector.detect(content);
        } catch (DidupParseException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256, ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Parsing fallito: " + ex.getMessage());
        } catch (RuntimeException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256,
                    "Errore imprevisto durante il riconoscimento del formato: " + ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Riconoscimento formato fallito");
        }

        if (kind == DidupFileKind.DIARY) {
            return importDiary(content, file, fileName, sha256);
        }
        return importHomework(content, file, fileName, sha256);
    }

    private ImportOutcome importHomework(
            byte[] content, Path file, DidupFileName fileName, String sha256) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();
        DidupFileParser.ParsedDidupFile parsed;
        try {
            parsed = homeworkParser.parse(content);
        } catch (DidupParseException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256, ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Parsing fallito: " + ex.getMessage());
        } catch (RuntimeException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256,
                    "Errore imprevisto durante il parsing: " + ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Parsing fallito");
        }

        Instant importedAt = Instant.now();
        String importId = UUID.randomUUID().toString();
        ImportReference reference =
                new ImportReference(importId, sourceCode, filename, importedAt);

        HomeworkAssignmentWriter.ImportResult result;
        try {
            result = assignmentWriter.importAssignments(parsed.assignments(), reference);
        } catch (DataAccessException ex) {
            return persistenceError(fileName, sha256, ex);
        } catch (RuntimeException ex) {
            log.error("Errore durante il salvataggio dei compiti di {}", filename, ex);
            return ImportOutcome.error(sourceCode, filename, "Salvataggio non confermato");
        }

        HomeworkImport document = toDocument(importId, fileName, sha256, importedAt, parsed);
        document.setCreatedCount(result.created());
        document.setUpdatedCount(result.updated());
        document.setUnchangedCount(result.unchanged());

        HomeworkImport saved;
        try {
            saved = importRepository.save(document);
            if (saved.getId() == null || importRepository.findBySourceCode(sourceCode).isEmpty()) {
                throw new IllegalStateException("Salvataggio non confermato da MongoDB");
            }
        } catch (DuplicateKeyException ex) {
            return handleConcurrentInsert(file, fileName, sha256);
        } catch (DataAccessException ex) {
            return persistenceError(fileName, sha256, ex);
        } catch (RuntimeException ex) {
            log.error("Errore durante il salvataggio dell'importazione {}", filename, ex);
            return ImportOutcome.error(sourceCode, filename, "Salvataggio non confermato");
        }

        List<HomeworkAssignment> createdAssignments =
                assignmentRepository.findByFirstImportId(importId);
        return completeImport(
                file,
                fileName,
                sha256,
                parsed.assignments().size(),
                homeworkMessage(result),
                deleted -> publishHomeworkCompleted(saved, parsed, result, createdAssignments, deleted),
                result.created() > 0 ? saved.getId() : null);
    }

    private ImportOutcome importDiary(
            byte[] content, Path file, DidupFileName fileName, String sha256) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();
        DidupDiaryParser.ParsedDiaryFile parsed;
        try {
            parsed = diaryParser.parse(content);
        } catch (DidupParseException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256, ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Parsing diario fallito: " + ex.getMessage());
        } catch (RuntimeException ex) {
            issueService.record(ImportIssue.Type.PARSE_ERROR, filename, sourceCode, sha256,
                    "Errore imprevisto durante il parsing del diario: " + ex.getMessage());
            return ImportOutcome.error(sourceCode, filename, "Parsing diario fallito");
        }

        Instant importedAt = Instant.now();
        String importId = UUID.randomUUID().toString();
        ImportReference reference =
                new ImportReference(importId, sourceCode, filename, importedAt);

        DiaryEntryWriter.ImportResult result;
        try {
            result = diaryEntryWriter.importEntries(parsed.entries(), reference);
        } catch (DataAccessException ex) {
            return persistenceError(fileName, sha256, ex);
        } catch (RuntimeException ex) {
            log.error("Errore durante il salvataggio del diario {}", filename, ex);
            return ImportOutcome.error(sourceCode, filename, "Salvataggio non confermato");
        }

        DiaryImport document = toDiaryDocument(importId, fileName, sha256, importedAt, parsed);
        document.setCreatedCount(result.created());
        document.setUpdatedCount(result.updated());
        document.setUnchangedCount(result.unchanged());

        DiaryImport saved;
        try {
            saved = diaryImportRepository.save(document);
            if (saved.getId() == null || diaryImportRepository.findBySourceCode(sourceCode).isEmpty()) {
                throw new IllegalStateException("Salvataggio non confermato da MongoDB");
            }
        } catch (DuplicateKeyException ex) {
            return handleConcurrentInsert(file, fileName, sha256);
        } catch (DataAccessException ex) {
            return persistenceError(fileName, sha256, ex);
        } catch (RuntimeException ex) {
            log.error("Errore durante il salvataggio dell'importazione diario {}", filename, ex);
            return ImportOutcome.error(sourceCode, filename, "Salvataggio non confermato");
        }

        return completeImport(
                file,
                fileName,
                sha256,
                parsed.entries().size(),
                diaryMessage(result),
                deleted -> publishDiaryImported(saved, parsed, result, deleted),
                null);
    }

    private ImportOutcome handleConcurrentInsert(
            Path file, DidupFileName fileName, String sha256) {
        Optional<HomeworkImport> homework = importRepository.findBySourceCode(fileName.sourceCode());
        Optional<DiaryImport> diary = diaryImportRepository.findBySourceCode(fileName.sourceCode());
        if (homework.isPresent() || diary.isPresent()) {
            return handleExisting(homework, diary, file, fileName, sha256);
        }
        Optional<HomeworkImport> sameHomeworkContent = importRepository.findFirstBySha256(sha256);
        Optional<DiaryImport> sameDiaryContent = diaryImportRepository.findFirstBySha256(sha256);
        if (sameHomeworkContent.isPresent() || sameDiaryContent.isPresent()) {
            return handleSameContent(sameHomeworkContent, sameDiaryContent, file, fileName, sha256);
        }
        issueService.record(
                ImportIssue.Type.PERSISTENCE_ERROR,
                fileName.originalFilename(),
                fileName.sourceCode(),
                sha256,
                "Violazione di unicità senza documento esistente");
        return ImportOutcome.error(fileName.sourceCode(), fileName.originalFilename(), "Errore di persistenza");
    }

    /**
     * Lo stesso contenuto può essere condiviso più volte da DidUP con un codice
     * file diverso. Lo SHA-256 identifica il file: la copia viene eliminata
     * senza riscrivere compiti o attività.
     */
    private ImportOutcome handleSameContent(
            Optional<HomeworkImport> homework,
            Optional<DiaryImport> diary,
            Path file,
            DidupFileName fileName,
            String sha256) {
        int existingCount;
        if (homework.isPresent()) {
            existingCount = homework.get().getAssignmentCount();
        } else if (diary.isPresent()) {
            existingCount = diary.get().getEntryCount();
        } else {
            return ImportOutcome.error(fileName.sourceCode(), fileName.originalFilename(),
                    "Contenuto duplicato senza documento esistente");
        }

        boolean deleted = false;
        if (file != null && properties.getImports().isDeleteAfterImport()) {
            deleted = safelyDelete(file, fileName, sha256);
        }
        return new ImportOutcome(
                ImportOutcome.Status.ALREADY_IMPORTED,
                fileName.sourceCode(),
                fileName.originalFilename(),
                existingCount,
                deleted
                        ? "Contenuto già importato con un altro codice; copia rimossa"
                        : "Contenuto già importato con un altro codice; nessuna nuova importazione");
    }

    private ImportOutcome persistenceError(DidupFileName fileName, String sha256, DataAccessException ex) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();
        log.error("MongoDB non disponibile durante l'importazione di {}", filename, ex);
        try {
            issueService.record(ImportIssue.Type.PERSISTENCE_ERROR, filename, sourceCode, sha256,
                    "Errore MongoDB: " + ex.getMessage());
        } catch (RuntimeException issueError) {
            log.warn("Impossibile registrare l'anomalia di persistenza: {}", issueError.getMessage());
        }
        return ImportOutcome.error(sourceCode, filename, "Errore di persistenza MongoDB");
    }

    /**
     * Coda comune di ogni importazione riuscita: cancellazione sicura,
     * risoluzione anomalie, evento realtime e notifica dei soli compiti nuovi.
     */
    private ImportOutcome completeImport(
            Path file,
            DidupFileName fileName,
            String sha256,
            int itemCount,
            String baseMessage,
            Consumer<Boolean> publication,
            String notificationImportId) {
        boolean deleted = false;
        if (file != null && properties.getImports().isDeleteAfterImport()) {
            deleted = safelyDelete(file, fileName, sha256);
        }

        try {
            issueService.resolveFileIssues(fileName.originalFilename());
        } catch (RuntimeException ex) {
            log.warn("Impossibile risolvere le anomalie di {}: {}", fileName.originalFilename(), ex.getMessage());
        }

        try {
            publication.accept(deleted);
        } catch (RuntimeException ex) {
            log.warn("Pubblicazione dell'evento di importazione fallita per {}: {}",
                    fileName.originalFilename(), ex.getMessage());
        }

        if (notificationImportId != null) {
            try {
                notificationService.notifyNewImport(notificationImportId);
            } catch (RuntimeException ex) {
                log.warn("Invio notifica nuove assegnazioni fallito per import {}: {}",
                        notificationImportId, ex.getMessage());
            }
        }

        String message = file == null
                ? baseMessage
                : (deleted ? baseMessage : baseMessage + "; il file non è stato eliminato");
        return new ImportOutcome(
                ImportOutcome.Status.IMPORTED, fileName.sourceCode(), fileName.originalFilename(), itemCount, message);
    }

    private ImportOutcome handleExisting(
            Optional<HomeworkImport> homework,
            Optional<DiaryImport> diary,
            Path file,
            DidupFileName fileName,
            String sha256) {
        String filename = fileName.originalFilename();
        String sourceCode = fileName.sourceCode();
        if (homework.isPresent() && diary.isPresent()) {
            issueService.recordOnce(
                    ImportIssue.Type.DUPLICATE_CONFLICT,
                    filename,
                    sourceCode,
                    sha256,
                    "Il codice " + sourceCode
                            + " è presente sia tra i compiti sia nel diario. Il nuovo file non è stato importato né eliminato.");
            return ImportOutcome.conflict(sourceCode, filename,
                    "Conflitto: codice già usato da compiti e diario");
        }

        String existingSha;
        int existingCount;
        String kindLabel;
        if (homework.isPresent()) {
            existingSha = homework.get().getSha256();
            existingCount = homework.get().getAssignmentCount();
            kindLabel = "compiti";
        } else {
            existingSha = diary.get().getSha256();
            existingCount = diary.get().getEntryCount();
            kindLabel = "diario";
        }

        if (!Objects.equals(existingSha, sha256)) {
            issueService.recordOnce(
                    ImportIssue.Type.DUPLICATE_CONFLICT,
                    filename,
                    sourceCode,
                    sha256,
                    "Il codice " + sourceCode + " è già stato importato ("
                            + kindLabel + ") con un contenuto diverso (SHA-256 "
                            + existingSha + "). Il nuovo file non è stato importato né eliminato.");
            return ImportOutcome.conflict(sourceCode, filename,
                    "Conflitto: codice già importato con SHA-256 differente");
        }

        boolean deleted = false;
        if (file != null && properties.getImports().isDeleteAfterImport()) {
            deleted = safelyDelete(file, fileName, sha256);
        }
        return new ImportOutcome(
                ImportOutcome.Status.ALREADY_IMPORTED,
                sourceCode,
                filename,
                existingCount,
                deleted ? "File già importato; copia rimossa" : "File già importato; nessuna nuova importazione");
    }

    private boolean safelyDelete(Path file, DidupFileName fileName, String expectedSha256) {
        String filename = fileName.originalFilename();
        if (!isDirectChildOfConfiguredDirectory(file)) {
            issueService.recordOnce(ImportIssue.Type.FILE_DELETION_FAILED, filename, fileName.sourceCode(),
                    expectedSha256, "Rifiutata la cancellazione di un file esterno alla directory configurata");
            return false;
        }
        try {
            if (Files.isSymbolicLink(file)) {
                issueService.recordOnce(ImportIssue.Type.FILE_DELETION_FAILED, filename, fileName.sourceCode(),
                        expectedSha256, "Rifiutata la cancellazione di un link simbolico");
                return false;
            }
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                issueService.resolveDeletionIssues(filename, expectedSha256);
                return true;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                issueService.recordOnce(ImportIssue.Type.FILE_DELETION_FAILED, filename, fileName.sourceCode(),
                        expectedSha256, "Il percorso non è più un file regolare");
                return false;
            }
            String currentSha = sha256(Files.readAllBytes(file));
            if (!currentSha.equals(expectedSha256)) {
                issueService.recordOnce(
                        ImportIssue.Type.FILE_CHANGED_DURING_IMPORT,
                        filename,
                        fileName.sourceCode(),
                        currentSha,
                        "Il file è cambiato durante l'importazione: il contenuto non corrisponde a quello salvato. "
                                + "Il file non è stato eliminato.");
                return false;
            }
            Files.delete(file);
            issueService.resolveDeletionIssues(filename, expectedSha256);
            return true;
        } catch (IOException | SecurityException ex) {
            log.warn("Cancellazione di {} fallita: {}", filename, ex.getMessage());
            try {
                issueService.record(ImportIssue.Type.FILE_DELETION_FAILED, filename, fileName.sourceCode(),
                        expectedSha256, "Impossibile eliminare il file: " + ex.getMessage());
            } catch (RuntimeException issueError) {
                log.warn("Impossibile registrare l'errore di cancellazione: {}", issueError.getMessage());
            }
            return false;
        }
    }

    private boolean isDirectChildOfConfiguredDirectory(Path file) {
        Path root = Path.of(properties.getImports().getDirectory()).toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        return target.getParent() != null && target.getParent().equals(root);
    }

    private HomeworkImport toDocument(
            String importId,
            DidupFileName fileName,
            String sha256,
            Instant importedAt,
            DidupFileParser.ParsedDidupFile parsed) {
        HomeworkImport document = new HomeworkImport();
        document.setId(importId);
        document.setSourceCode(fileName.sourceCode());
        document.setOriginalFilename(fileName.originalFilename());
        document.setFilenameSuffix(fileName.filenameSuffix());
        document.setSha256(sha256);
        document.setImportedAt(importedAt);
        document.setSchoolYearStart(parsed.schoolYearStart());
        document.setAssignmentCount(parsed.assignments().size());
        return document;
    }

    private DiaryImport toDiaryDocument(
            String importId,
            DidupFileName fileName,
            String sha256,
            Instant importedAt,
            DidupDiaryParser.ParsedDiaryFile parsed) {
        DiaryImport document = new DiaryImport();
        document.setId(importId);
        document.setSourceCode(fileName.sourceCode());
        document.setOriginalFilename(fileName.originalFilename());
        document.setFilenameSuffix(fileName.filenameSuffix());
        document.setSha256(sha256);
        document.setImportedAt(importedAt);
        document.setSchoolYearStart(parsed.schoolYearStart());
        document.setEntryCount(parsed.entries().size());
        return document;
    }

    private void publishHomeworkCompleted(
            HomeworkImport saved,
            DidupFileParser.ParsedDidupFile parsed,
            HomeworkAssignmentWriter.ImportResult result,
            List<HomeworkAssignment> createdAssignments,
            boolean fileDeleted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", DidupFileKind.HOMEWORK.name());
        payload.put("importId", saved.getId());
        payload.put("sourceCode", saved.getSourceCode());
        payload.put("filename", saved.getOriginalFilename());
        payload.put("assignmentCount", saved.getAssignmentCount());
        payload.put("createdCount", result.created());
        payload.put("updatedCount", result.updated());
        payload.put("unchangedCount", result.unchanged());
        payload.put("fileDeleted", fileDeleted);

        Map<String, Long> bySubject = new LinkedHashMap<>();
        Set<String> subjects = new LinkedHashSet<>();
        createdAssignments.forEach(assignment -> {
            subjects.add(assignment.getSubject());
            bySubject.merge(assignment.getSubject(), 1L, Long::sum);
        });
        payload.put("subjects", List.copyOf(subjects));
        payload.put("subjectCounts", bySubject);
        createdAssignments.stream()
                .map(HomeworkAssignment::getDueDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .ifPresent(date -> payload.put("earliestDueDate", date.toString()));
        payload.put("allSubjects", parsed.assignments().stream()
                .map(DidupFileParser.ParsedAssignment::subject)
                .distinct()
                .toList());
        events.publish(MidaEvents.IMPORT_COMPLETED, payload);
    }

    private void publishDiaryImported(
            DiaryImport saved,
            DidupDiaryParser.ParsedDiaryFile parsed,
            DiaryEntryWriter.ImportResult result,
            boolean fileDeleted) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("importId", saved.getId());
        payload.put("sourceCode", saved.getSourceCode());
        payload.put("filename", saved.getOriginalFilename());
        payload.put("entryCount", saved.getEntryCount());
        payload.put("createdCount", result.created());
        payload.put("updatedCount", result.updated());
        payload.put("unchangedCount", result.unchanged());
        payload.put("fileDeleted", fileDeleted);
        Set<String> subjects = new LinkedHashSet<>();
        parsed.entries().forEach(entry -> subjects.add(entry.subject()));
        payload.put("subjects", List.copyOf(subjects));
        payload.put("activityDates", parsed.entries().stream()
                .map(DidupDiaryParser.ParsedDiaryEntry::activityDate)
                .distinct()
                .sorted()
                .map(LocalDate::toString)
                .toList());
        events.publish(MidaEvents.DIARY_IMPORTED, payload);
    }

    private String homeworkMessage(HomeworkAssignmentWriter.ImportResult result) {
        List<String> parts = new ArrayList<>();
        if (result.created() > 0) {
            parts.add(result.created() + (result.created() == 1 ? " nuovo compito" : " nuovi compiti"));
        }
        if (result.updated() > 0) {
            parts.add(result.updated() + (result.updated() == 1 ? " aggiornato" : " aggiornati"));
        }
        if (result.unchanged() > 0) {
            parts.add(result.unchanged() + (result.unchanged() == 1 ? " già presente" : " già presenti"));
        }
        if (parts.isEmpty()) {
            return "Nessun compito nel file";
        }
        if (result.created() == 0 && result.updated() == 0) {
            return "Nessuna novità: " + String.join(", ", parts);
        }
        return String.join(", ", parts);
    }

    private String diaryMessage(DiaryEntryWriter.ImportResult result) {
        List<String> parts = new ArrayList<>();
        if (result.created() > 0) {
            parts.add(result.created() + (result.created() == 1 ? " nuova attività" : " nuove attività"));
        }
        if (result.updated() > 0) {
            parts.add(result.updated() + (result.updated() == 1 ? " aggiornata" : " aggiornate"));
        }
        if (result.unchanged() > 0) {
            parts.add(result.unchanged() + (result.unchanged() == 1 ? " già presente" : " già presenti"));
        }
        if (parts.isEmpty()) {
            return "Nessuna attività nel file";
        }
        if (result.created() == 0 && result.updated() == 0) {
            return "Nessuna novità: " + String.join(", ", parts);
        }
        return String.join(", ", parts);
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 non disponibile", ex);
        }
    }

    public record ImportOutcome(
            Status status,
            String sourceCode,
            String filename,
            int assignmentCount,
            String message) {

        public enum Status {
            IMPORTED,
            ALREADY_IMPORTED,
            CONFLICT,
            ERROR,
            SKIPPED
        }

        static ImportOutcome error(String sourceCode, String filename, String message) {
            return new ImportOutcome(Status.ERROR, sourceCode, filename, 0, message);
        }

        static ImportOutcome conflict(String sourceCode, String filename, String message) {
            return new ImportOutcome(Status.CONFLICT, sourceCode, filename, 0, message);
        }

        static ImportOutcome skipped(String sourceCode, String filename, String message) {
            return new ImportOutcome(Status.SKIPPED, sourceCode, filename, 0, message);
        }
    }
}
