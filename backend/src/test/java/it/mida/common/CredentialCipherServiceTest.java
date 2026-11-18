package it.mida.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherServiceTest {

    private final CredentialCipherService service = new CredentialCipherService();

    @Test
    void encryptsAndDecryptsWithBase64Key() {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);
        String keyMaterial = Base64.getEncoder().encodeToString(key);

        String first = service.encrypt("refresh-token-segreto", keyMaterial);
        String second = service.encrypt("refresh-token-segreto", keyMaterial);

        assertThat(first).startsWith("v1:");
        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first, keyMaterial)).isEqualTo("refresh-token-segreto");
        assertThat(service.decrypt(second, keyMaterial)).isEqualTo("refresh-token-segreto");
    }

    @Test
    void derivesAKeyFromPassphraseWhenItIsNotBase64() {
        String keyMaterial = "0f7c4b9e-8d21-4f5a-9c3e-2b6d1a8e7c55";

        String encrypted = service.encrypt("refresh-token", keyMaterial);

        assertThat(service.decrypt(encrypted, keyMaterial)).isEqualTo("refresh-token");
    }

    @Test
    void returnsNullForBlankValues() {
        assertThat(service.encrypt("", "key")).isNull();
        assertThat(service.decrypt(null, "key")).isNull();
    }

    @Test
    void rejectsTamperedCiphertext() {
        String keyMaterial = "chiave-di-test";
        String encrypted = service.encrypt("token", keyMaterial);

        assertThatThrownBy(() -> service.decrypt(
                encrypted.substring(0, encrypted.length() - 2), keyMaterial))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDecryptionWithAWrongKey() {
        String encrypted = service.encrypt("token", "prima-chiave");

        assertThatThrownBy(() -> service.decrypt(encrypted, "seconda-chiave"))
                .isInstanceOf(IllegalStateException.class);
    }
}
