package it.mida.google.email;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Allegato di un'email. Il contenuto binario vive in GridFS; questo documento
 * conserva i metadati, l'esito dell'importazione DidUP e il riferimento per il
 * download.
 */
@Document(collection = "emailAttachments")
@CompoundIndex(
        name = "ux_email_attachments_message_provider",
        def = "{'emailMessageId': 1, 'providerAttachmentId': 1}",
        unique = true)
public class EmailAttachment {

    public static final String IMPORT_IMPORTED = "IMPORTED";
    public static final String IMPORT_ALREADY = "ALREADY_IMPORTED";
    public static final String IMPORT_NOT_DIDUP = "NOT_DIDUP";
    public static final String IMPORT_FAILED = "FAILED";
    public static final String IMPORT_TOO_LARGE = "TOO_LARGE";
    public static final String IMPORT_STORED = "STORED";

    @Id
    private String id;

    @Indexed(name = "ix_email_attachments_message")
    private String emailMessageId;

    private String providerAttachmentId;

    private String filename;

    private String mimeType;

    private long size;

    @Indexed(name = "ix_email_attachments_sha256")
    private String sha256;

    private String gridFsId;

    private boolean inline;

    private String contentId;

    private String importStatus;

    private String importMessage;

    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmailMessageId() {
        return emailMessageId;
    }

    public void setEmailMessageId(String emailMessageId) {
        this.emailMessageId = emailMessageId;
    }

    public String getProviderAttachmentId() {
        return providerAttachmentId;
    }

    public void setProviderAttachmentId(String providerAttachmentId) {
        this.providerAttachmentId = providerAttachmentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getGridFsId() {
        return gridFsId;
    }

    public void setGridFsId(String gridFsId) {
        this.gridFsId = gridFsId;
    }

    public boolean isInline() {
        return inline;
    }

    public void setInline(boolean inline) {
        this.inline = inline;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public String getImportStatus() {
        return importStatus;
    }

    public void setImportStatus(String importStatus) {
        this.importStatus = importStatus;
    }

    public String getImportMessage() {
        return importMessage;
    }

    public void setImportMessage(String importMessage) {
        this.importMessage = importMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
