package it.mida.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.mida.config.MidaProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class GoogleApiClientTest {

    @Test
    void buildsOfflineAuthorizationUrlWithAllScopes() {
        GoogleClientCredentialsService credentials = mock(GoogleClientCredentialsService.class);
        when(credentials.configured()).thenReturn(true);
        when(credentials.requireConfig()).thenReturn(new GoogleClientCredentialsService.ClientConfig(
                "client-id",
                "client-secret",
                "http://localhost:8200/api/google/oauth/callback",
                "mida-project"));
        GoogleApiClient client = new GoogleApiClient(
                new MidaProperties(), credentials, new ObjectMapper());

        assertThat(client.isConfigured()).isTrue();
        String url = client.authorizationUrl("state-1");

        assertThat(url)
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
                .contains("client_id=client-id")
                .contains("response_type=code")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("state=state-1")
                .contains("classroom.courses.readonly")
                .contains("drive.readonly")
                .contains("gmail.readonly");
    }

    @Test
    void refusesToBuildAuthorizationUrlWithoutImportedCredentials() {
        GoogleClientCredentialsService credentials = mock(GoogleClientCredentialsService.class);
        GoogleApiClient client = new GoogleApiClient(
                new MidaProperties(), credentials, new ObjectMapper());

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.authorizationUrl("state"))
                .isInstanceOf(GoogleNotConfiguredException.class);
    }

    @Test
    void refusesToBuildAuthorizationUrlWhenGoogleIsDisabled() {
        MidaProperties properties = new MidaProperties();
        properties.getGoogle().setEnabled(false);
        GoogleClientCredentialsService credentials = mock(GoogleClientCredentialsService.class);
        when(credentials.configured()).thenReturn(true);
        GoogleApiClient client = new GoogleApiClient(properties, credentials, new ObjectMapper());

        assertThat(client.isConfigured()).isFalse();
    }
}
