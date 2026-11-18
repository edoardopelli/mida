package it.mida.google;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Credenziali del client OAuth importate dal JSON scaricato da Google Cloud.
 * Il JSON completo è cifrato AES-256-GCM; la chiave UUID viene generata
 * dall'applicazione e salvata nello stesso documento, come richiesto.
 */
@Document(collection = "googleClientCredentials")
public class GoogleClientCredentials {

    public static final String DEFAULT_ID = "default";

    @Id
    private String id = DEFAULT_ID;

    private String projectId;

    private String clientId;

    private String redirectUri;

    private String encryptionKey;

    private String credentialsJson;

    private Instant createdAt;

    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String getCredentialsJson() {
        return credentialsJson;
    }

    public void setCredentialsJson(String credentialsJson) {
        this.credentialsJson = credentialsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
