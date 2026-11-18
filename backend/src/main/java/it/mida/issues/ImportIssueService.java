package it.mida.issues;

import it.mida.events.MidaEvents;
import it.mida.events.MidaEventPublisher;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ImportIssueService {

    private final ImportIssueRepository repository;
    private final MidaEventPublisher events;

    public ImportIssueService(ImportIssueRepository repository, MidaEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public ImportIssue record(
            ImportIssue.Type type,
            String filename,
            String sourceCode,
            String sha256,
            String description) {
        String fingerprint = fingerprint(type, filename, sha256);
        Instant now = Instant.now();
        Optional<ImportIssue> existing = repository.findByFingerprint(fingerprint);
        ImportIssue issue;
        boolean isNew = false;
        if (existing.isPresent()) {
            issue = existing.get();
            if (issue.getStatus() == ImportIssue.Status.RESOLVED) {
                issue.setStatus(ImportIssue.Status.OPEN);
                issue.setResolvedAt(null);
                isNew = true;
            }
            issue.setAttempts(issue.getAttempts() + 1);
            issue.setLastSeenAt(now);
            issue.setDescription(description);
        } else {
            issue = new ImportIssue();
            issue.setFingerprint(fingerprint);
            issue.setType(type);
            issue.setFilename(filename);
            issue.setSourceCode(sourceCode);
            issue.setSha256(sha256);
            issue.setDescription(description);
            issue.setFirstSeenAt(now);
            issue.setLastSeenAt(now);
            issue.setAttempts(1);
            issue.setStatus(ImportIssue.Status.OPEN);
            isNew = true;
        }
        ImportIssue saved = repository.save(issue);
        if (isNew) {
            events.publish(MidaEvents.IMPORT_ISSUE, toDto(saved));
        }
        return saved;
    }

    public ImportIssue recordOnce(
            ImportIssue.Type type,
            String filename,
            String sourceCode,
            String sha256,
            String description) {
        String fingerprint = fingerprint(type, filename, sha256);
        Optional<ImportIssue> existing = repository.findByFingerprint(fingerprint);
        if (existing.isPresent() && existing.get().getStatus() == ImportIssue.Status.OPEN) {
            return existing.get();
        }
        return record(type, filename, sourceCode, sha256, description);
    }

    public boolean hasOpenIssue(String filename, String sha256, Collection<ImportIssue.Type> types) {
        return repository.existsByFilenameAndSha256AndStatusAndTypeIn(
                filename, sha256, ImportIssue.Status.OPEN, types);
    }

    public void resolveDeletionIssues(String filename) {
        List<ImportIssue> issues = repository.findByFilenameAndTypeAndStatus(
                filename, ImportIssue.Type.FILE_DELETION_FAILED, ImportIssue.Status.OPEN);
        for (ImportIssue issue : issues) {
            resolve(issue);
        }
    }

    public void resolveOpenIssues(ImportIssue.Type type, String filename) {
        List<ImportIssue> issues = repository.findByFilenameAndTypeAndStatus(
                filename, type, ImportIssue.Status.OPEN);
        for (ImportIssue issue : issues) {
            resolve(issue);
        }
    }

    /**
     * A successful import proves that the problems previously observed on the
     * same file are no longer present. Conflicts, deletion failures and
     * directory issues have their own resolution paths and are left alone.
     */
    public void resolveFileIssues(String filename) {
        List<ImportIssue> issues = repository.findByFilenameAndStatus(
                filename, ImportIssue.Status.OPEN);
        for (ImportIssue issue : issues) {
            if (issue.getType() != ImportIssue.Type.DUPLICATE_CONFLICT
                    && issue.getType() != ImportIssue.Type.FILE_DELETION_FAILED
                    && issue.getType() != ImportIssue.Type.DIRECTORY_UNAVAILABLE) {
                resolve(issue);
            }
        }
    }

    public void resolveDeletionIssues(String filename, String sha256) {
        List<ImportIssue> issues = repository.findByFilenameAndTypeAndStatus(
                filename, ImportIssue.Type.FILE_DELETION_FAILED, ImportIssue.Status.OPEN);
        for (ImportIssue issue : issues) {
            if (issue.getSha256() == null || issue.getSha256().equals(sha256)) {
                resolve(issue);
            }
        }
    }

    private void resolve(ImportIssue issue) {
        issue.setStatus(ImportIssue.Status.RESOLVED);
        issue.setResolvedAt(Instant.now());
        issue.setLastSeenAt(Instant.now());
        ImportIssue saved = repository.save(issue);
        events.publish(MidaEvents.IMPORT_ISSUE, toDto(saved));
    }

    public static ImportIssueDto toDto(ImportIssue issue) {
        return new ImportIssueDto(
                issue.getId(),
                issue.getType(),
                issue.getStatus(),
                issue.getFilename(),
                issue.getSourceCode(),
                issue.getSha256(),
                issue.getDescription(),
                issue.getFirstSeenAt(),
                issue.getLastSeenAt(),
                issue.getAttempts(),
                issue.getResolvedAt());
    }

    private String fingerprint(ImportIssue.Type type, String filename, String sha256) {
        return type.name() + "|" + (filename == null ? "" : filename) + "|" + (sha256 == null ? "" : sha256);
    }

    public record ImportIssueDto(
            String id,
            ImportIssue.Type type,
            ImportIssue.Status status,
            String filename,
            String sourceCode,
            String sha256,
            String description,
            Instant firstSeenAt,
            Instant lastSeenAt,
            int attempts,
            Instant resolvedAt) {
    }
}
