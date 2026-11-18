package it.mida.google;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Connessione OAuth all'account Google. Il refresh token è cifrato con
 * AES-GCM: non viene mai restituito dalle API né scritto nei log.
 */
@Document(collection = "googleConnection")
public class GoogleConnection {

    public static final String DEFAULT_ID = "google";

    @Id
    private String id = DEFAULT_ID;

    /**
     * Indirizzo dell'account Google, cifrato con AES-256-GCM come il refresh
     * token. Viene decifrato solo per lo stato mostrato nell'interfaccia.
     */
    private String accountEmail;

    private String refreshToken;

    private String scopes;

    private Instant connectedAt;

    private Instant lastSyncAt;

    private String lastSyncStatus;

    private String lastError;

    private int lastClassroomItemCount;

    private int lastNewClassroomItemCount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
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

    public int getLastClassroomItemCount() {
        return lastClassroomItemCount;
    }

    public void setLastClassroomItemCount(int lastClassroomItemCount) {
        this.lastClassroomItemCount = lastClassroomItemCount;
    }

    public int getLastNewClassroomItemCount() {
        return lastNewClassroomItemCount;
    }

    public void setLastNewClassroomItemCount(int lastNewClassroomItemCount) {
        this.lastNewClassroomItemCount = lastNewClassroomItemCount;
    }
}
