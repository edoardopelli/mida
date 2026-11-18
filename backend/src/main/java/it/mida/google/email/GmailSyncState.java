package it.mida.google.email;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stato dell'ultima sincronizzazione Gmail. Separato dalla connessione OAuth
 * per non sovrascrivere i contatori Classroom durante sync concorrenti.
 */
@Document(collection = "gmailSyncState")
public class GmailSyncState {

    public static final String DEFAULT_ID = "gmail";

    @Id
    private String id = DEFAULT_ID;

    private Instant lastSyncAt;

    private String lastSyncStatus = "NEVER";

    private String lastError;

    private int lastFetchedCount;

    private int lastNewCount;

    private int lastAttachmentCount;

    private int lastNewAttachmentCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public void setLastSyncStatus(String lastSyncStatus) {
        this.lastSyncStatus = lastSyncStatus;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public int getLastFetchedCount() {
        return lastFetchedCount;
    }

    public void setLastFetchedCount(int lastFetchedCount) {
        this.lastFetchedCount = lastFetchedCount;
    }

    public int getLastNewCount() {
        return lastNewCount;
    }

    public void setLastNewCount(int lastNewCount) {
        this.lastNewCount = lastNewCount;
    }

    public int getLastAttachmentCount() {
        return lastAttachmentCount;
    }

    public void setLastAttachmentCount(int lastAttachmentCount) {
        this.lastAttachmentCount = lastAttachmentCount;
    }

    public int getLastNewAttachmentCount() {
        return lastNewAttachmentCount;
    }

    public void setLastNewAttachmentCount(int lastNewAttachmentCount) {
        this.lastNewAttachmentCount = lastNewAttachmentCount;
    }
}
