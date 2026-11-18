package it.mida.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import it.mida.common.CredentialCipherService;
import it.mida.config.MidaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleConnectionServiceTest {

    @Mock
    private GoogleApiClient api;
    @Mock
    private GoogleConnectionRepository connectionRepository;
    @Mock
    private GoogleSettingsRepository settingsRepository;
    @Mock
    private GoogleOAuthStateRepository stateRepository;
    @Mock
    private CredentialCipherService secretService;
    @Mock
    private GoogleClientCredentialsService clientCredentials;

    private GoogleConnectionService service;

    @BeforeEach
    void setUp() {
        service = new GoogleConnectionService(
                new MidaProperties(),
                api,
                connectionRepository,
                settingsRepository,
                stateRepository,
                secretService,
                clientCredentials);
    }

    @Test
    void decryptsTheAccountEmailBeforeExposingIt() {
        GoogleConnection connection = new GoogleConnection();
        connection.setAccountEmail("v1:iv:ciphertext");
        when(clientCredentials.encryptionKey()).thenReturn("uuid-key");
        when(secretService.decrypt("v1:iv:ciphertext", "uuid-key"))
                .thenReturn("studente@scuola.example");

        assertThat(service.accountEmail(connection)).isEqualTo("studente@scuola.example");
    }

    @Test
    void keepsLegacyPlaintextAccountEmailReadable() {
        GoogleConnection connection = new GoogleConnection();
        connection.setAccountEmail("studente@scuola.example");

        assertThat(service.accountEmail(connection)).isEqualTo("studente@scuola.example");
    }
}
