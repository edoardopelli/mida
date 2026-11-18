package it.mida.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Cifra valori sensibili con AES-256-GCM usando una chiave fornita dal
 * chiamante. La chiave può essere una stringa Base64 di 32 byte oppure una
 * passphrase (es. UUID), derivata con SHA-256.
 */
@Service
public class CredentialCipherService {

    private static final String PREFIX = "v1";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();

    public String encrypt(String plaintext, String keyMaterial) {
        if (!StringUtils.hasText(plaintext)) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + ":" + base64(iv) + ":" + base64(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("Impossibile cifrare le credenziali", ex);
        }
    }

    public String decrypt(String value, String keyMaterial) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split(":");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new IllegalStateException("Formato delle credenziali cifrate non riconosciuto");
        }
        try {
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(keyMaterial), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Impossibile decifrare le credenziali: reimporta il JSON client OAuth", ex);
        }
    }

    private SecretKeySpec key(String material) throws Exception {
        if (!StringUtils.hasText(material)) {
            throw new IllegalStateException("Chiave di cifratura assente");
        }
        byte[] raw;
        try {
            byte[] decoded = Base64.getDecoder().decode(material.strip());
            raw = decoded.length == KEY_BYTES ? decoded : sha256(material);
        } catch (IllegalArgumentException ex) {
            raw = sha256(material);
        }
        return new SecretKeySpec(raw, "AES");
    }

    private byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
