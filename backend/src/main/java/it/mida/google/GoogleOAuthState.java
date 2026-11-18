package it.mida.google;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stato OAuth monouso, valido pochi minuti, per proteggere il callback da
 * richieste CSRF.
 */
@Document(collection = "googleOauthStates")
public class GoogleOAuthState {

    @Id
    private String state;

    private Instant createdAt;

    @Indexed(name = "ttl_google_oauth_states", expireAfterSeconds = 0)
    private Instant expiresAt;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
