package it.mida.diary;

import it.mida.common.ImportReference;
import it.mida.common.SemanticText;
import it.mida.parser.DidupDiaryParser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Scrive il diario una riga per ora. La coppia data + ora è l'identità: se il
 * docente modifica la descrizione, la materia o il professore della stessa
 * ora, il documento esistente viene aggiornato e la revisione aumenta.
 */
@Service
public class DiaryEntryWriter {

    private final DiaryEntryRepository repository;

    public DiaryEntryWriter(DiaryEntryRepository repository) {
        this.repository = repository;
    }

    public ImportResult importEntries(
            List<DidupDiaryParser.ParsedDiaryEntry> entries, ImportReference reference) {
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (DidupDiaryParser.ParsedDiaryEntry parsed : entries) {
            Outcome outcome = importOne(parsed, reference, null);
            switch (outcome) {
                case CREATED -> created++;
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
            }
        }
        return new ImportResult(created, updated, unchanged);
    }

    public Outcome importLegacy(LegacyEntry legacy, ImportReference reference) {
        DidupDiaryParser.ParsedDiaryEntry parsed = new DidupDiaryParser.ParsedDiaryEntry(
                legacy.activityDate(),
                legacy.activityDateRaw(),
                legacy.period(),
                legacy.periodLabel(),
                legacy.subject(),
                legacy.teacher(),
                legacy.description());
        return importOne(parsed, reference, legacy);
    }

    private Outcome importOne(
            DidupDiaryParser.ParsedDiaryEntry parsed,
            ImportReference reference,
            LegacyEntry legacy) {
        String contentHash = contentHash(parsed);
        Instant now = Instant.now();
        Optional<DiaryEntry> existing =
                repository.findByActivityDateAndPeriod(parsed.activityDate(), parsed.period());
        if (existing.isPresent()) {
            DiaryEntry entry = existing.get();
            if (contentHash.equals(entry.getContentHash())) {
                entry.setLastSeenAt(now);
                repository.save(entry);
                return Outcome.UNCHANGED;
            }
            applyContent(entry, parsed, contentHash);
            entry.setRevision(Math.max(1, entry.getRevision()) + 1);
            entry.setImportId(reference.importId());
            entry.setSourceCode(reference.sourceCode());
            entry.setOriginalFilename(reference.originalFilename());
            entry.setImportedAt(reference.importedAt());
            entry.setLastSeenAt(now);
            entry.setUpdatedAt(now);
            repository.save(entry);
            return Outcome.UPDATED;
        }

        DiaryEntry entry = new DiaryEntry();
        entry.setId(legacy != null && legacy.id() != null ? legacy.id() : UUID.randomUUID().toString());
        applyContent(entry, parsed, contentHash);
        entry.setRevision(1);
        entry.setImportId(reference.importId());
        entry.setSourceCode(reference.sourceCode());
        entry.setOriginalFilename(reference.originalFilename());
        entry.setImportedAt(reference.importedAt());
        entry.setFirstImportId(reference.importId());
        entry.setFirstSeenAt(now);
        entry.setLastSeenAt(now);
        entry.setUpdatedAt(now);
        try {
            repository.save(entry);
            return Outcome.CREATED;
        } catch (DuplicateKeyException ex) {
            // Inserimento concorrente sulla stessa data/ora: aggiorna la riga
            // già presente invece di duplicarla.
            DiaryEntry concurrent = repository
                    .findByActivityDateAndPeriod(parsed.activityDate(), parsed.period())
                    .orElseThrow(() -> ex);
            if (contentHash.equals(concurrent.getContentHash())) {
                concurrent.setLastSeenAt(now);
                repository.save(concurrent);
                return Outcome.UNCHANGED;
            }
            applyContent(concurrent, parsed, contentHash);
            concurrent.setRevision(Math.max(1, concurrent.getRevision()) + 1);
            concurrent.setImportId(reference.importId());
            concurrent.setSourceCode(reference.sourceCode());
            concurrent.setOriginalFilename(reference.originalFilename());
            concurrent.setImportedAt(reference.importedAt());
            concurrent.setLastSeenAt(now);
            concurrent.setUpdatedAt(now);
            repository.save(concurrent);
            return Outcome.UPDATED;
        }
    }

    private void applyContent(
            DiaryEntry entry, DidupDiaryParser.ParsedDiaryEntry parsed, String contentHash) {
        entry.setActivityDate(parsed.activityDate());
        entry.setActivityDateRaw(parsed.activityDateRaw());
        entry.setPeriod(parsed.period());
        entry.setPeriodLabel(parsed.periodLabel());
        entry.setSubject(parsed.subject());
        entry.setTeacher(parsed.teacher());
        entry.setDescription(parsed.description());
        entry.setContentHash(contentHash);
    }

    public static String contentHash(DidupDiaryParser.ParsedDiaryEntry entry) {
        return SemanticText.digest(
                entry.activityDate().toString(),
                Integer.toString(entry.period()),
                entry.subject(),
                entry.teacher(),
                entry.description());
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

    public record LegacyEntry(
            String id,
            LocalDate activityDate,
            String activityDateRaw,
            int period,
            String periodLabel,
            String subject,
            String teacher,
            String description) {
    }
}
