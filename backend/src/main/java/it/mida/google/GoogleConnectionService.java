package it.mida.google;

import tools.jackson.databind.JsonNode;
import it.mida.common.CredentialCipherService;
import it.mida.config.MidaProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Gestisce il ciclo di vita OAuth: stato anti-CSRF, scambio del codice,
 * refresh dell'access token, riconnessione e disconnessione.
 */
@Service
public class GoogleConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GoogleConnectionService.class);
    private static final long STATE_MINUTES = 10;

    private final MidaProperties properties;
    private final GoogleApiClient api;
    private final GoogleConnectionRepository connectionRepository;
    private final GoogleSettingsRepository settingsRepository;
    private final GoogleOAuthStateRepository stateRepository;
    private final CredentialCipherService secretService;
    private final GoogleClientCredentialsService clientCredentials;

    private volatile CachedToken cachedToken;

    public GoogleConnectionService(
            MidaProperties properties,
            GoogleApiClient api,
            GoogleConnectionRepository connectionRepository,
            GoogleSettingsRepository settingsRepository,
            GoogleOAuthStateRepository stateRepository,
            CredentialCipherService secretService,
            GoogleClientCredentialsService clientCredentials) {
        this.properties = properties;
        this.api = api;
        this.connectionRepository = connectionRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.secretService = secretService;
        this.clientCredentials = clientCredentials;
    }

    public boolean configured() {
        return api.isConfigured();
    }

    public Optional<GoogleConnection> connection() {
        return connectionRepository.findById(GoogleConnection.DEFAULT_ID);
    }

    public GoogleConnection requireConnection() {
        return connection().orElseThrow(GoogleNotConnectedException::new);
    }

    public GoogleSettings settings() {
        return settingsRepository.findById(GoogleSettings.DEFAULT_ID).orElseGet(() -> {
            GoogleSettings defaults = new GoogleSettings();
            defaults.setGmailQuery(properties.getGoogle().getGmailQuery());
            defaults.setGmailEnabled(properties.getGoogle().isGmailEnabled());
            defaults.setClassroomEnabled(properties.getGoogle().isClassroomEnabled());
            return defaults;
        });
    }

    public GoogleSettings updateSettings(
            String gmailQuery, boolean gmailEnabled, boolean classroomEnabled) {
        String query = StringUtils.hasText(gmailQuery)
                ? gmailQuery.strip()
                : properties.getGoogle().getGmailQuery();
        if (query.length() > 500) {
            throw new IllegalArgumentException("La ricerca Gmail non può superare 500 caratteri");
        }
        GoogleSettings settings = new GoogleSettings();
        settings.setId(GoogleSettings.DEFAULT_ID);
        settings.setGmailQuery(query);
        settings.setGmailEnabled(gmailEnabled);
        settings.setClassroomEnabled(classroomEnabled);
        settings.setUpdatedAt(Instant.now());
        return settingsRepository.save(settings);
    }

    public String connectUrl() {
        if (!configured()) {
            throw new GoogleNotConfiguredException();
        }
        GoogleOAuthState state = new GoogleOAuthState();
        state.setState(UUID.randomUUID().toString());
        state.setCreatedAt(Instant.now());
        state.setExpiresAt(Instant.now().plus(STATE_MINUTES, ChronoUnit.MINUTES));
        stateRepository.save(state);
        return api.authorizationUrl(state.getState());
    }

    public GoogleConnection handleCallback(String code, String state) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new IllegalArgumentException("Risposta OAuth incompleta");
        }
        GoogleOAuthState stored = stateRepository.findById(state)
                .orElseThrow(() -> new IllegalArgumentException("Stato OAuth non valido o scaduto"));
        stateRepository.deleteById(state);
        if (stored.getExpiresAt() == null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Stato OAuth scaduto: riprova a collegare l'account");
        }

        GoogleApiClient.TokenResponse token = api.exchangeCode(code);
        if (!StringUtils.hasText(token.refreshToken())) {
            throw new IllegalStateException(
                    "Google non ha restituito un refresh token. Rimuovi l'accesso precedente dalle impostazioni "
                            + "dell'account Google e riprova.");
        }
        JsonNode user = api.userInfo(token.accessToken());
        String email = user.path("email").asText(null);

        GoogleConnection connection = connectionRepository
                .findById(GoogleConnection.DEFAULT_ID)
                .orElseGet(GoogleConnection::new);
        String encryptionKey = clientCredentials.encryptionKey();
        connection.setId(GoogleConnection.DEFAULT_ID);
        connection.setAccountEmail(secretService.encrypt(email, encryptionKey));
        connection.setRefreshToken(secretService.encrypt(token.refreshToken(), encryptionKey));
        connection.setScopes(StringUtils.hasText(token.scope())
                ? token.scope()
                : String.join(" ", api.scopes()));
        connection.setConnectedAt(Instant.now());
        connection.setLastSyncStatus("OK");
        connection.setLastError(null);
        cachedToken = new CachedToken(
                token.accessToken(), Instant.now().plusSeconds(Math.max(60, token.expiresInSeconds() - 60)));
        GoogleConnection saved = connectionRepository.save(connection);
        ensureDefaultSettings(email);
        log.info("Account Google collegato: {}", email == null ? "(email non disponibile)" : email);
        return saved;
    }

    private void ensureDefaultSettings(String email) {
        if (settingsRepository.findById(GoogleSettings.DEFAULT_ID).isPresent()) {
            return;
        }
        GoogleSettings settings = new GoogleSettings();
        settings.setId(GoogleSettings.DEFAULT_ID);
        settings.setGmailQuery(defaultGmailQuery(email));
        settings.setGmailEnabled(properties.getGoogle().isGmailEnabled());
        settings.setClassroomEnabled(properties.getGoogle().isClassroomEnabled());
        settings.setUpdatedAt(Instant.now());
        settingsRepository.save(settings);
    }

    private String defaultGmailQuery(String email) {
        if (StringUtils.hasText(email) && email.contains("@")) {
            String domain = email.substring(email.indexOf('@') + 1);
            if (!"gmail.com".equalsIgnoreCase(domain) && !"googlemail.com".equalsIgnoreCase(domain)) {
                return "from:" + domain + " OR from:didup";
            }
        }
        return properties.getGoogle().getGmailQuery();
    }

    public String accessToken() {
        CachedToken cached = cachedToken;
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }
        synchronized (this) {
            cached = cachedToken;
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                return cached.value();
            }
            GoogleConnection connection = requireConnection();
            String refreshToken = secretService.decrypt(
                    connection.getRefreshToken(), clientCredentials.encryptionKey());
            if (!StringUtils.hasText(refreshToken)) {
                throw new GoogleReauthRequiredException("Refresh token Google assente: riconnetti l'account");
            }
            try {
                GoogleApiClient.TokenResponse token = api.refresh(refreshToken);
                if (StringUtils.hasText(token.refreshToken())) {
                    connection.setRefreshToken(secretService.encrypt(
                            token.refreshToken(), clientCredentials.encryptionKey()));
                }
                connectionRepository.save(connection);
                cachedToken = new CachedToken(
                        token.accessToken(),
                        Instant.now().plusSeconds(Math.max(60, token.expiresInSeconds() - 60)));
                return token.accessToken();
            } catch (GoogleReauthRequiredException ex) {
                connection.setLastSyncStatus("REAUTH_REQUIRED");
                connection.setLastError(ex.getMessage());
                connectionRepository.save(connection);
                throw ex;
            }
        }
    }

    /**
     * Decifra l'indirizzo dell'account per mostrarlo nell'interfaccia. I
     * documenti creati prima dell'introduzione della cifratura contengono
     * ancora il valore in chiaro: in quel caso viene restituito così com'è.
     */
    public String accountEmail(GoogleConnection connection) {
        if (connection == null || !StringUtils.hasText(connection.getAccountEmail())) {
            return null;
        }
        String value = connection.getAccountEmail();
        return value.startsWith("v1:")
                ? secretService.decrypt(value, clientCredentials.encryptionKey())
                : value;
    }

    public void disconnect() {
        connection().ifPresent(connection -> {
            try {
                String refreshToken = secretService.decrypt(
                        connection.getRefreshToken(), clientCredentials.encryptionKey());
                if (StringUtils.hasText(refreshToken)) {
                    api.revoke(refreshToken);
                }
            } catch (RuntimeException ex) {
                log.debug("Revoca del token Google non riuscita: {}", ex.getMessage());
            }
            connectionRepository.deleteById(GoogleConnection.DEFAULT_ID);
            cachedToken = null;
            log.info("Account Google scollegato");
        });
    }

    public GoogleConnection save(GoogleConnection connection) {
        return connectionRepository.save(connection);
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}
