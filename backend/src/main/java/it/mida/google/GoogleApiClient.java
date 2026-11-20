package it.mida.google;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.config.MidaProperties;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Client HTTP minimale per OAuth e per le API Google usate da MIDA. Non
 * dipende da librerie Google: le risposte sono JSON e i binari vengono
 * scaricati direttamente.
 */
@Component
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class GoogleApiClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiClient.class);

    public static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    public static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    public static final String REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";
    public static final String USERINFO_ENDPOINT = "https://openidconnect.googleapis.com/v1/userinfo";
    public static final String GMAIL_BASE = "https://gmail.googleapis.com/gmail/v1/users/me";
    public static final String CLASSROOM_BASE = "https://classroom.googleapis.com/v1";
    public static final String DRIVE_BASE = "https://www.googleapis.com/drive/v3";

    public static final List<String> SCOPES = List.of(
            "openid",
            "email",
            "profile",
            "https://www.googleapis.com/auth/gmail.readonly",
            "https://www.googleapis.com/auth/classroom.courses.readonly",
            "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
            "https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly",
            "https://www.googleapis.com/auth/classroom.announcements.readonly",
            "https://www.googleapis.com/auth/drive.readonly");

    private final MidaProperties properties;
    private final GoogleClientCredentialsService credentialsService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GoogleApiClient(
            MidaProperties properties,
            GoogleClientCredentialsService credentialsService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.credentialsService = credentialsService;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofMinutes(2));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isConfigured() {
        return properties.getGoogle().isEnabled() && credentialsService.configured();
    }

    public List<String> scopes() {
        return SCOPES;
    }

    public String authorizationUrl(String state) {
        if (!isConfigured()) {
            throw new GoogleNotConfiguredException();
        }
        GoogleClientCredentialsService.ClientConfig config = credentialsService.requireConfig();
        return UriComponentsBuilder.fromUriString(AUTH_ENDPOINT)
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", String.join(" ", SCOPES))
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    public TokenResponse exchangeCode(String code) {
        GoogleClientCredentialsService.ClientConfig config = credentialsService.requireConfig();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("redirect_uri", config.redirectUri());
        form.add("grant_type", "authorization_code");
        return tokenRequest(form, false);
    }

    public TokenResponse refresh(String refreshToken) {
        GoogleClientCredentialsService.ClientConfig config = credentialsService.requireConfig();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", refreshToken);
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("grant_type", "refresh_token");
        return tokenRequest(form, true);
    }

    public JsonNode userInfo(String accessToken) {
        return getJson(USERINFO_ENDPOINT, accessToken);
    }

    public JsonNode getJson(String url, String accessToken) {
        for (int attempt = 0; ; attempt++) {
            try {
                String body = restClient.get()
                        .uri(URI.create(url))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .body(String.class);
                if (!StringUtils.hasText(body)) {
                    throw new GoogleApiException("Risposta vuota da Google");
                }
                return parse(body);
            } catch (RestClientResponseException ex) {
                if (retryable(ex) && attempt < 2) {
                    pause(attempt);
                    continue;
                }
                throw toApiException(ex);
            }
        }
    }

    public BinaryResponse getBytes(String url, String accessToken) {
        for (int attempt = 0; ; attempt++) {
            try {
                ResponseEntity<byte[]> response = restClient.get()
                        .uri(URI.create(url))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .retrieve()
                        .toEntity(byte[].class);
                byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
                String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                String contentLength = response.getHeaders().getFirst(HttpHeaders.CONTENT_LENGTH);
                return new BinaryResponse(body, contentType, contentLength);
            } catch (RestClientResponseException ex) {
                if (retryable(ex) && attempt < 2) {
                    pause(attempt);
                    continue;
                }
                throw toApiException(ex);
            }
        }
    }

    private boolean retryable(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(500L * (attempt + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GoogleApiException("Sincronizzazione interrotta", ex);
        }
    }

    public void revoke(String token) {
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("token", token);
            restClient.post()
                    .uri(REVOKE_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.debug("Revoca del token Google non riuscita: {}", ex.getMessage());
        }
    }

    public static String withQuery(String url, Map<String, String> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        new LinkedHashMap<>(parameters).forEach((name, value) -> {
            if (StringUtils.hasText(value)) {
                builder.queryParam(name, value);
            }
        });
        return builder.build().encode().toUriString();
    }

    private TokenResponse tokenRequest(MultiValueMap<String, String> form, boolean refresh) {
        try {
            String body = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(body)) {
                throw new GoogleApiException("Risposta OAuth priva di access_token");
            }
            JsonNode response = parse(body);
            if (!response.hasNonNull("access_token")) {
                throw new GoogleApiException("Risposta OAuth priva di access_token");
            }
            return new TokenResponse(
                    response.path("access_token").asText(),
                    response.hasNonNull("refresh_token")
                            ? response.path("refresh_token").asText()
                            : null,
                    response.path("expires_in").asLong(3600),
                    response.path("scope").asText(null),
                    response.path("token_type").asText("Bearer"));
        } catch (RestClientResponseException ex) {
            String errorCode = errorCode(ex);
            if ("invalid_grant".equals(errorCode)) {
                throw new GoogleReauthRequiredException(
                        "L'autorizzazione Google è scaduta o è stata revocata. Riconnetti l'account.");
            }
            throw toApiException(ex);
        }
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            throw new GoogleApiException("Risposta JSON di Google non valida", ex);
        }
    }

    private String errorCode(RestClientResponseException ex) {
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            String code = node.path("error").asText("");
            return code;
        } catch (Exception ignored) {
            return "";
        }
    }

    private GoogleApiException toApiException(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String message = "Errore Google (" + status + ")";
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            JsonNode error = node.path("error");
            if (error.isObject() && error.hasNonNull("message")) {
                message = error.path("message").asText(message);
            } else if (error.isTextual() && !error.asText().isBlank()) {
                message = error.asText();
            }
        } catch (Exception ignored) {
            if (StringUtils.hasText(ex.getResponseBodyAsString())) {
                message = ex.getResponseBodyAsString();
            }
        }
        if (status == 401) {
            return new GoogleReauthRequiredException(message);
        }
        return new GoogleApiException(status, message, ex);
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            String scope,
            String tokenType) {
    }

    public record BinaryResponse(byte[] body, String contentType, String contentLength) {
    }
}
