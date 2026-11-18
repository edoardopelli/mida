package it.mida.homework;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Storico di un singolo file DidUP Family. I compiti non sono più incorporati:
 * ogni compito vive nella collezione {@code homeworkAssignments} e viene
 * riconosciuto tra importazioni successive.
 */
@Document(collection = "homeworkImports")
public class HomeworkImport {

    @Id
    private String id;

    @Indexed(name = "ux_homework_imports_source_code", unique = true)
    private String sourceCode;

    private String originalFilename;

    private Integer filenameSuffix;

    @Indexed(name = "ix_homework_imports_sha256")
    private String sha256;

    private Instant importedAt;

    /**
     * Numero di compiti presenti nel file. Include anche quelli già noti.
     */
    private int assignmentCount;

    private int createdCount;

    private int updatedCount;

    private int unchangedCount;

    private int schoolYearStart;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getFilenameSuffix() {
        return filenameSuffix;
    }

    public void setFilenameSuffix(Integer filenameSuffix) {
        this.filenameSuffix = filenameSuffix;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public int getAssignmentCount() {
        return assignmentCount;
    }

    public void setAssignmentCount(int assignmentCount) {
        this.assignmentCount = assignmentCount;
    }

    public int getSchoolYearStart() {
        return schoolYearStart;
    }

    public void setSchoolYearStart(int schoolYearStart) {
        this.schoolYearStart = schoolYearStart;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public void setUnchangedCount(int unchangedCount) {
        this.unchangedCount = unchangedCount;
    }
}
