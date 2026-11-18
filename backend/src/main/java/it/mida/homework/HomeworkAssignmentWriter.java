package it.mida.homework;

import it.mida.common.ImportReference;
import it.mida.common.SemanticText;
import it.mida.parser.DidupFileParser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Scrive i compiti uno alla volta e riconosce quelli già presenti.
 *
 * <p>L'identità non può dipendere dal codice del file: DidUP genera un codice
 * nuovo a ogni esportazione. Vengono quindi usate tre impronte:</p>
 * <ul>
 *   <li>{@code contentHash}: contenuto esatto (data, materia, professore,
 *       consegna e descrizione normalizzati);</li>
 *   <li>{@code slotKey}: stessa data di assegnazione, materia, professore e
 *       consegna, usata per riconoscere una modifica del testo;</li>
 *   <li>{@code baseKey}: come sopra ma senza la consegna, usata quando il
 *       docente sposta soltanto la scadenza.</li>
 * </ul>
 *
 * <p>La deduplica è conservativa: due testi chiaramente diversi restano due
 * compiti distinti, anche se condividono materia e date.</p>
 */
@Service
public class HomeworkAssignmentWriter {

    private final HomeworkAssignmentRepository repository;

    public HomeworkAssignmentWriter(HomeworkAssignmentRepository repository) {
        this.repository = repository;
    }

    public ImportResult importAssignments(
            List<DidupFileParser.ParsedAssignment> assignments, ImportReference reference) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (DidupFileParser.ParsedAssignment parsed : assignments) {
            Outcome outcome = importOne(parsed, reference, false, null);
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        return new ImportResult(created, updated, unchanged);
    }

    /**
     * Importa un compito proveniente dal vecchio modello a documenti
     * incorporati. Conserva l'id originale e lo stato "completato", così la
     * migrazione non perde le azioni dell'utente.
     */
    public Outcome importLegacy(LegacyAssignment legacy, ImportReference reference) {
        DidupFileParser.ParsedAssignment parsed = new DidupFileParser.ParsedAssignment(
                legacy.assignedDate(),
                legacy.assignedDateRaw(),
                legacy.dueDate(),
                legacy.dueDateRaw(),
                legacy.subject(),
                legacy.teacher(),
                legacy.description());
        return importOne(parsed, reference, true, legacy);
    }

    private Outcome importOne(
            DidupFileParser.ParsedAssignment parsed,
            ImportReference reference,
            boolean legacy,
            LegacyAssignment legacyValues) {
        String contentHash = contentHash(parsed);
        String slotKey = slotKey(parsed);
        String baseKey = baseKey(parsed);
        String descriptionHash = SemanticText.digest(parsed.description());
        Instant now = Instant.now();

        Optional<HomeworkAssignment> exact = repository.findByContentHash(contentHash);
        if (exact.isPresent()) {
            HomeworkAssignment existing = exact.get();
            touch(existing, reference, now);
            mergeCompletion(existing, legacy, legacyValues);
            repository.save(existing);
            return Outcome.UNCHANGED;
        }

        HomeworkAssignment revision = bestRevisionCandidate(repository.findBySlotKey(slotKey), parsed);
        if (revision == null) {
            revision = movedDeadlineCandidate(repository.findByBaseKey(baseKey), descriptionHash);
        }
        if (revision != null) {
            applyRevision(revision, parsed, contentHash, slotKey, baseKey, descriptionHash, reference, now);
            repository.save(revision);
            return Outcome.UPDATED;
        }

        HomeworkAssignment created = newAssignment(
                parsed, contentHash, slotKey, baseKey, descriptionHash, reference, now);
        if (legacy && legacyValues != null && legacyValues.id() != null) {
            created.setId(legacyValues.id());
        }
        if (legacy && legacyValues != null) {
            created.setCompleted(legacyValues.completed());
            created.setCompletedAt(legacyValues.completedAt());
        }
        try {
            repository.save(created);
            return Outcome.CREATED;
        } catch (DuplicateKeyException ex) {
            // Un'altra scansione ha inserito lo stesso contenuto nello stesso
            // istante: recupera il documento e trattalo come invariato.
            Optional<HomeworkAssignment> concurrent = repository.findByContentHash(contentHash);
            if (concurrent.isPresent()) {
                HomeworkAssignment existing = concurrent.get();
                touch(existing, reference, now);
                mergeCompletion(existing, legacy, legacyValues);
                repository.save(existing);
                return Outcome.UNCHANGED;
            }
            throw ex;
        }
    }

    private HomeworkAssignment bestRevisionCandidate(
            List<HomeworkAssignment> candidates, DidupFileParser.ParsedAssignment parsed) {
        HomeworkAssignment best = null;
        double bestScore = -1;
        for (HomeworkAssignment candidate : candidates) {
            double score = SemanticText.revisionScore(candidate.getDescription(), parsed.description());
            if (score < 0) {
                continue;
            }
            if (score > bestScore || (score == bestScore && newer(candidate, best))) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean newer(HomeworkAssignment candidate, HomeworkAssignment current) {
        if (current == null) {
            return true;
        }
        Instant candidateAt = candidate.getUpdatedAt() != null
                ? candidate.getUpdatedAt()
                : candidate.getImportedAt();
        Instant currentAt = current.getUpdatedAt() != null
                ? current.getUpdatedAt()
                : current.getImportedAt();
        if (candidateAt == null) {
            return false;
        }
        return currentAt == null || candidateAt.isAfter(currentAt);
    }

    /**
     * Riconosce un compito con la stessa descrizione ma una data di consegna
     * diversa. Si applica solo quando il candidato è univoco: in caso di
     * ambiguità è più sicuro creare un nuovo compito che sovrascrivere quello
     * sbagliato.
     */
    private HomeworkAssignment movedDeadlineCandidate(
            List<HomeworkAssignment> candidates, String descriptionHash) {
        List<HomeworkAssignment> matches = candidates.stream()
                .filter(candidate -> descriptionHash.equals(candidate.getDescriptionHash()))
                .toList();
        if (matches.size() != 1) {
            return null;
        }
        return matches.getFirst();
    }

    private HomeworkAssignment newAssignment(
            DidupFileParser.ParsedAssignment parsed,
            String contentHash,
            String slotKey,
            String baseKey,
            String descriptionHash,
            ImportReference reference,
            Instant now) {
        HomeworkAssignment assignment = new HomeworkAssignment();
        assignment.setId(UUID.randomUUID().toString());
        applyContent(assignment, parsed, contentHash, slotKey, baseKey, descriptionHash);
        assignment.setRevision(1);
        assignment.setImportId(reference.importId());
        assignment.setSourceCode(reference.sourceCode());
        assignment.setOriginalFilename(reference.originalFilename());
        assignment.setImportedAt(reference.importedAt());
        assignment.setFirstImportId(reference.importId());
        assignment.setFirstSeenAt(now);
        assignment.setLastSeenAt(now);
        assignment.setUpdatedAt(now);
        return assignment;
    }

    private void applyRevision(
            HomeworkAssignment assignment,
            DidupFileParser.ParsedAssignment parsed,
            String contentHash,
            String slotKey,
            String baseKey,
            String descriptionHash,
            ImportReference reference,
            Instant now) {
        applyContent(assignment, parsed, contentHash, slotKey, baseKey, descriptionHash);
        assignment.setRevision(Math.max(1, assignment.getRevision()) + 1);
        assignment.setImportId(reference.importId());
        assignment.setSourceCode(reference.sourceCode());
        assignment.setOriginalFilename(reference.originalFilename());
        assignment.setImportedAt(reference.importedAt());
        assignment.setLastSeenAt(now);
        assignment.setUpdatedAt(now);
    }

    private void applyContent(
            HomeworkAssignment assignment,
            DidupFileParser.ParsedAssignment parsed,
            String contentHash,
            String slotKey,
            String baseKey,
            String descriptionHash) {
        assignment.setContentHash(contentHash);
        assignment.setSlotKey(slotKey);
        assignment.setBaseKey(baseKey);
        assignment.setDescriptionHash(descriptionHash);
        assignment.setAssignedDate(parsed.assignedDate());
        assignment.setAssignedDateRaw(parsed.assignedDateRaw());
        assignment.setDueDate(parsed.dueDate());
        assignment.setDueDateRaw(parsed.dueDateRaw());
        assignment.setSubject(parsed.subject());
        assignment.setTeacher(parsed.teacher());
        assignment.setDescription(parsed.description());
    }

    private void touch(HomeworkAssignment assignment, ImportReference reference, Instant now) {
        assignment.setLastSeenAt(now);
        // L'importazione che ha solo ritrovato il compito non ne cambia
        // l'origine: sourceCode e importedAt restano quelli dell'ultima
        // modifica reale.
    }

    private void mergeCompletion(
            HomeworkAssignment assignment, boolean legacy, LegacyAssignment legacyValues) {
        if (!legacy || legacyValues == null || !legacyValues.completed()) {
            return;
        }
        if (!assignment.isCompleted()) {
            assignment.setCompleted(true);
            assignment.setCompletedAt(legacyValues.completedAt());
        } else if (assignment.getCompletedAt() == null && legacyValues.completedAt() != null) {
            assignment.setCompletedAt(legacyValues.completedAt());
        }
    }

    public static String contentHash(DidupFileParser.ParsedAssignment assignment) {
        return SemanticText.digest(
                assignment.assignedDate().toString(),
                assignment.dueDate().toString(),
                assignment.subject(),
                assignment.teacher(),
                assignment.description());
    }

    public static String slotKey(DidupFileParser.ParsedAssignment assignment) {
        return SemanticText.digest(
                assignment.assignedDate().toString(),
                assignment.dueDate().toString(),
                assignment.subject(),
                assignment.teacher());
    }

    public static String baseKey(DidupFileParser.ParsedAssignment assignment) {
        return SemanticText.digest(
                assignment.assignedDate().toString(),
                assignment.subject(),
                assignment.teacher());
    }

    public enum Outcome {
        CREATED,
        UPDATED,
        UNCHANGED
    }

    public record ImportResult(int created, int updated, int unchanged) {

        public int total() {
            return created + updated + unchanged;
        }
    }

    public record LegacyAssignment(
            String id,
            LocalDate assignedDate,
            String assignedDateRaw,
            LocalDate dueDate,
            String dueDateRaw,
            String subject,
            String teacher,
            String description,
            boolean completed,
            Instant completedAt) {
    }
}
