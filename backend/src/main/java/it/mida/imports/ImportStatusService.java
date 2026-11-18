package it.mida.imports;

import it.mida.diary.DiaryEntry;
import it.mida.diary.DiaryEntryRepository;
import it.mida.diary.DiaryImport;
import it.mida.diary.DiaryImportRepository;
import it.mida.homework.HomeworkAssignment;
import it.mida.homework.HomeworkAssignmentRepository;
import it.mida.homework.HomeworkImport;
import it.mida.homework.HomeworkImportRepository;
import it.mida.issues.ImportIssue;
import it.mida.issues.ImportIssueRepository;
import it.mida.notifications.PushSubscriptionRepository;
import java.time.Instant;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class ImportStatusService {

    private final MonitorState monitorState;
    private final HomeworkImportRepository importRepository;
    private final DiaryImportRepository diaryImportRepository;
    private final HomeworkAssignmentRepository assignmentRepository;
    private final DiaryEntryRepository diaryEntryRepository;
    private final ImportIssueRepository issueRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final MongoTemplate mongoTemplate;

    public ImportStatusService(
            MonitorState monitorState,
            HomeworkImportRepository importRepository,
            DiaryImportRepository diaryImportRepository,
            HomeworkAssignmentRepository assignmentRepository,
            DiaryEntryRepository diaryEntryRepository,
            ImportIssueRepository issueRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            MongoTemplate mongoTemplate) {
        this.monitorState = monitorState;
        this.importRepository = importRepository;
        this.diaryImportRepository = diaryImportRepository;
        this.assignmentRepository = assignmentRepository;
        this.diaryEntryRepository = diaryEntryRepository;
        this.issueRepository = issueRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public ImportStatusDto status() {
        long totalImports = importRepository.count();
        long totalDiaryImports = diaryImportRepository.count();
        // Il totale riflette i compiti e le ore unici, non le righe dei file:
        // lo stesso compito presente in più esportazioni conta una volta sola.
        long totalAssignments = assignmentRepository.count();
        long totalDiaryEntries = diaryEntryRepository.count();
        long openIssues = issueRepository.countByStatus(ImportIssue.Status.OPEN);
        Instant lastHomeworkImport = importRepository.findFirstByOrderByImportedAtDesc()
                .map(HomeworkImport::getImportedAt)
                .orElse(null);
        Instant lastDiaryImport = diaryImportRepository.findFirstByOrderByImportedAtDesc()
                .map(DiaryImport::getImportedAt)
                .orElse(null);
        Instant lastImport = latest(lastHomeworkImport, lastDiaryImport);
        return new ImportStatusDto(
                monitorState.getState() != MonitorState.State.DISABLED,
                monitorState.getState().name(),
                monitorState.getLastScanAt(),
                lastImport,
                totalImports,
                totalAssignments,
                totalDiaryImports,
                totalDiaryEntries,
                openIssues,
                pushSubscriptionRepository.countByActiveTrue(),
                monitorState.isDirectoryReadable(),
                monitorState.getLastError(),
                Instant.now());
    }

    private Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    /**
     * Conta i documenti non ancora migrati dal vecchio modello incorporato.
     * Serve solo alla diagnostica della migrazione.
     */
    public long legacyEmbeddedCount() {
        long assignments = mongoTemplate.count(
                Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("assignments").exists(true)),
                "homeworkImports");
        long entries = mongoTemplate.count(
                Query.query(org.springframework.data.mongodb.core.query.Criteria
                        .where("entries").exists(true)),
                "diaryImports");
        return assignments + entries;
    }

    public record ImportStatusDto(
            boolean monitoringEnabled,
            String state,
            Instant lastScanAt,
            Instant lastSuccessfulImportAt,
            long totalImports,
            long totalAssignments,
            long totalDiaryImports,
            long totalDiaryEntries,
            long openIssues,
            long activePushSubscriptions,
            boolean directoryReadable,
            String lastError,
            Instant generatedAt) {
    }
}
