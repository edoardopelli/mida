package it.mida.homework;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un singolo compito. È un documento autonomo, non più un elemento incorporato
 * nell'importazione: lo stesso compito può comparire in più file DidUP senza
 * creare duplicati.
 *
 * <p>{@code contentHash} identifica il contenuto esatto; {@code slotKey}
 * raggruppa i compiti dello stesso giorno, materia, professore e consegna;
 * {@code baseKey} consente di riconoscere lo stesso compito quando cambia
 * soltanto la data di consegna.</p>
 */
@Document(collection = "homeworkAssignments")
public class HomeworkAssignment {

    @Id
    private String id;

    private String contentHash;

    private String slotKey;

    private String baseKey;

    private String descriptionHash;

    private LocalDate assignedDate;

    private String assignedDateRaw;

    private LocalDate dueDate;

    private String dueDateRaw;

    private String subject;

    private String teacher;

    private String description;

    private boolean completed;

    private Instant completedAt;

    /**
     * 1 alla prima importazione; aumenta quando il docente modifica il testo o
     * la data di consegna dello stesso compito.
     */
    private int revision;

    /**
     * Ultima importazione che ha creato o modificato il compito.
     */
    private String importId;

    private String sourceCode;

    private String originalFilename;

    private Instant importedAt;

    /**
     * Prima importazione in cui il compito è comparso.
     */
    private String firstImportId;

    private Instant firstSeenAt;

    private Instant lastSeenAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getSlotKey() {
        return slotKey;
    }

    public void setSlotKey(String slotKey) {
        this.slotKey = slotKey;
    }

    public String getBaseKey() {
        return baseKey;
    }

    public void setBaseKey(String baseKey) {
        this.baseKey = baseKey;
    }

    public String getDescriptionHash() {
        return descriptionHash;
    }

    public void setDescriptionHash(String descriptionHash) {
        this.descriptionHash = descriptionHash;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public String getAssignedDateRaw() {
        return assignedDateRaw;
    }

    public void setAssignedDateRaw(String assignedDateRaw) {
        this.assignedDateRaw = assignedDateRaw;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getDueDateRaw() {
        return dueDateRaw;
    }

    public void setDueDateRaw(String dueDateRaw) {
        this.dueDateRaw = dueDateRaw;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getImportId() {
        return importId;
    }

    public void setImportId(String importId) {
        this.importId = importId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getFirstImportId() {
        return firstImportId;
    }

    public void setFirstImportId(String firstImportId) {
        this.firstImportId = firstImportId;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
