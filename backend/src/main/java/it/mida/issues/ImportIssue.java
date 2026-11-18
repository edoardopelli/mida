package it.mida.issues;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An anomaly detected while scanning or importing a file. The fingerprint is
 * unique so that an unchanged file does not create a new issue on every poll.
 */
@Document(collection = "importIssues")
public class ImportIssue {

    public enum Type {
        DIRECTORY_UNAVAILABLE,
        FILE_TOO_LARGE,
        READ_ERROR,
        PARSE_ERROR,
        VALIDATION_ERROR,
        DUPLICATE_CONFLICT,
        FILE_DELETION_FAILED,
        FILE_CHANGED_DURING_IMPORT,
        PERSISTENCE_ERROR,
        UNEXPECTED_ERROR
    }

    public enum Status {
        OPEN,
        RESOLVED
    }

    @Id
    private String id;

    @Indexed(name = "ux_import_issues_fingerprint", unique = true)
    private String fingerprint;

    @Indexed(name = "ix_import_issues_status")
    private Status status = Status.OPEN;

    @Indexed(name = "ix_import_issues_type")
    private Type type;

    private String filename;

    private String sourceCode;

    private String sha256;

    private String description;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private int attempts;

    private Instant resolvedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
