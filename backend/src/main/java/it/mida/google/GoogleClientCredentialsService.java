package it.mida.google;

import it.mida.common.CredentialCipherService;
import it.mida.google.api.GoogleClientStatusDto;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Importa e gestisce il JSON del client OAuth scaricato da Google Cloud.
 * Il JSON completo (incluso il client secret) è cifrato; la chiave UUID è
 * generata dall'applicazione e salvata accanto al JSON.
 */
@Service
public class GoogleClientCredentialsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleClientCredentialsService.class);
    private static final String CALLBACK_PATH = "/api/google/oauth/callback";

    private final GoogleClientCredentialsRepository repository;
    private final CredentialCipherService cipher;
    private final ObjectMapper objectMapper;

    private volatile ClientConfig cached;
    private volatile String cachedKey;

    public GoogleClientCredentialsService(
            GoogleClientCredentialsRepository repository,
            CredentialCipherService cipher,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.cipher = cipher;
        this.objectMapper = objectMapper;
    }

    public boolean configured() {
        return repository.findById(GoogleClientCredentials.DEFAULT_ID)
                .map(credentials -> StringUtils.hasText(credentials.getCredentialsJson())
                        && StringUtils.hasText(credentials.getEncryptionKey()))
                .orElse(false);
    }

    public GoogleClientStatusDto status() {
        return repository.findById(GoogleClientCredentials.DEFAULT_ID)
                .map(credentials -> new GoogleClientStatusDto(
                        StringUtils.hasText(credentials.getCredentialsJson()),
                        credentials.getClientId(),
                        credentials.getProjectId(),
                        credentials.getRedirectUri(),
                        credentials.getUpdatedAt()))
                .orElseGet(() -> new GoogleClientStatusDto(false, null, null, null, null));
    }

    public GoogleClientStatusDto importJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            throw new IllegalArgumentException("Il file JSON è vuoto");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Il file non contiene un JSON valido", ex);
        }
        JsonNode web = root.has("web") ? root.path("web") : root.path("installed");
        if (!web.isObject()) {
            throw new IllegalArgumentException(
                    "Il JSON non contiene l'oggetto 'web' del client OAuth");
        }
        String clientId = web.path("client_id").asText("");
        String clientSecret = web.path("client_secret").asText("");
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            throw new IllegalArgumentException(
                    "Il JSON non contiene client_id e client_secret");
        }
        String redirectUri = callbackUri(web.path("redirect_uris"));
        if (redirectUri == null) {
            throw new IllegalArgumentException(
                    "Il JSON non contiene un redirect URI che termini con "
                            + CALLBACK_PATH
                            + ". Aggiungilo al client OAuth e scarica di nuovo il JSON.");
        }
        String projectId = web.path("project_id").asText(null);

        String encryptionKey = UUID.randomUUID().toString();
        GoogleClientCredentials credentials = new GoogleClientCredentials();
        credentials.setId(GoogleClientCredentials.DEFAULT_ID);
        credentials.setProjectId(projectId);
        credentials.setClientId(clientId);
        credentials.setRedirectUri(redirectUri);
        credentials.setEncryptionKey(encryptionKey);
        credentials.setCredentialsJson(cipher.encrypt(rawJson, encryptionKey));
        Instant now = Instant.now();
        credentials.setCreatedAt(now);
        credentials.setUpdatedAt(now);
        repository.save(credentials);

        cached = new ClientConfig(clientId, clientSecret, redirectUri, projectId);
        cachedKey = encryptionKey;
        log.info("Importato client OAuth Google per il progetto {}", projectId);
        return status();
    }

    public Optional<ClientConfig> config() {
        GoogleClientCredentials credentials = repository
                .findById(GoogleClientCredentials.DEFAULT_ID)
                .orElse(null);
        if (credentials == null
                || !StringUtils.hasText(credentials.getCredentialsJson())
                || !StringUtils.hasText(credentials.getEncryptionKey())) {
            return Optional.empty();
        }
        if (cached != null && credentials.getEncryptionKey().equals(cachedKey)) {
            return Optional.of(cached);
        }
        synchronized (this) {
            if (cached != null && credentials.getEncryptionKey().equals(cachedKey)) {
                return Optional.of(cached);
            }
            String json = cipher.decrypt(
                    credentials.getCredentialsJson(), credentials.getEncryptionKey());
            JsonNode root;
            try {
                root = objectMapper.readTree(json);
            } catch (Exception ex) {
                throw new IllegalStateException(
                        "Il JSON del client OAuth salvato non è più leggibile", ex);
            }
            JsonNode web = root.has("web") ? root.path("web") : root.path("installed");
            ClientConfig config = new ClientConfig(
                    web.path("client_id").asText(credentials.getClientId()),
                    web.path("client_secret").asText(),
                    web.path("redirect_uris").isArray()
                            ? callbackUri(web.path("redirect_uris"))
                            : credentials.getRedirectUri(),
                    web.path("project_id").asText(credentials.getProjectId()));
            cached = config;
            cachedKey = credentials.getEncryptionKey();
            return Optional.of(config);
        }
    }

    public ClientConfig requireConfig() {
        return config().orElseThrow(GoogleNotConfiguredException::new);
    }

    public String encryptionKey() {
        return repository.findById(GoogleClientCredentials.DEFAULT_ID)
                .map(GoogleClientCredentials::getEncryptionKey)
                .filter(StringUtils::hasText)
                .orElseThrow(GoogleNotConfiguredException::new);
    }

    public void delete() {
        repository.deleteById(GoogleClientCredentials.DEFAULT_ID);
        cached = null;
        cachedKey = null;
    }

    private String callbackUri(JsonNode redirectUris) {
        if (!redirectUris.isArray()) {
            return null;
        }
        for (JsonNode uri : redirectUris) {
            String value = uri.asText("");
            if (value.endsWith(CALLBACK_PATH)) {
                return value;
            }
        }
        return null;
    }

    public record ClientConfig(
            String clientId, String clientSecret, String redirectUri, String projectId) {
    }
}
