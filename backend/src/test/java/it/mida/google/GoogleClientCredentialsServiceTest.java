package it.mida.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.mida.common.CredentialCipherService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class GoogleClientCredentialsServiceTest {

    private static final String VALID_JSON = """
            {
              "web": {
                "client_id": "client-id.apps.googleusercontent.com",
                "project_id": "mida-project",
                "client_secret": "super-secret",
                "redirect_uris": [
                  "http://localhost:8200/api/google/oauth/callback"
                ]
              }
            }
            """;

    @Mock
    private GoogleClientCredentialsRepository repository;

    private GoogleClientCredentialsService service;

    @BeforeEach
    void setUp() {
        service = new GoogleClientCredentialsService(
                repository, new CredentialCipherService(), new ObjectMapper());
    }

    @Test
    void importsTheJsonEncryptingTheClientSecret() {
        when(repository.save(any(GoogleClientCredentials.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.importJson(VALID_JSON);

        ArgumentCaptor<GoogleClientCredentials> captor =
                ArgumentCaptor.forClass(GoogleClientCredentials.class);
        verify(repository).save(captor.capture());
        GoogleClientCredentials saved = captor.getValue();

        assertThat(saved.getClientId()).isEqualTo("client-id.apps.googleusercontent.com");
        assertThat(saved.getProjectId()).isEqualTo("mida-project");
        assertThat(saved.getRedirectUri())
                .isEqualTo("http://localhost:8200/api/google/oauth/callback");
        assertThat(saved.getEncryptionKey()).isNotBlank();
        assertThat(saved.getCredentialsJson()).startsWith("v1:");
        assertThat(saved.getCredentialsJson()).doesNotContain("super-secret");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void readsTheClientSecretBackFromTheEncryptedDocument() {
        when(repository.save(any(GoogleClientCredentials.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        service.importJson(VALID_JSON);
        ArgumentCaptor<GoogleClientCredentials> captor =
                ArgumentCaptor.forClass(GoogleClientCredentials.class);
        verify(repository).save(captor.capture());
        when(repository.findById(GoogleClientCredentials.DEFAULT_ID))
                .thenReturn(Optional.of(captor.getValue()));

        GoogleClientCredentialsService.ClientConfig config = service.requireConfig();

        assertThat(config.clientId()).isEqualTo("client-id.apps.googleusercontent.com");
        assertThat(config.clientSecret()).isEqualTo("super-secret");
        assertThat(config.redirectUri())
                .isEqualTo("http://localhost:8200/api/google/oauth/callback");
        assertThat(config.projectId()).isEqualTo("mida-project");
        assertThat(service.encryptionKey()).isEqualTo(captor.getValue().getEncryptionKey());
    }

    @Test
    void reportsNotConfiguredWhenNothingWasImported() {
        assertThat(service.configured()).isFalse();
        assertThat(service.status().configured()).isFalse();
        assertThatThrownBy(service::requireConfig)
                .isInstanceOf(GoogleNotConfiguredException.class);
    }

    @Test
    void rejectsJsonWithoutTheCallbackRedirectUri() {
        String json = """
                {
                  "web": {
                    "client_id": "client-id",
                    "client_secret": "secret",
                    "redirect_uris": ["http://localhost:8200/altro"]
                  }
                }
                """;

        assertThatThrownBy(() -> service.importJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/google/oauth/callback");
    }

    @Test
    void rejectsJsonWithoutClientCredentials() {
        assertThatThrownBy(() -> service.importJson("{\"web\": {}}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_id");
        assertThatThrownBy(() -> service.importJson("non-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JSON valido");
        assertThatThrownBy(() -> service.importJson("{\"altro\": true}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("web");
    }

    @Test
    void deleteRemovesTheStoredCredentials() {
        service.delete();

        verify(repository).deleteById(GoogleClientCredentials.DEFAULT_ID);
    }
}
