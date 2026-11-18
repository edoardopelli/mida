package it.mida.notifications;

import it.mida.config.MidaProperties;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Loads VAPID keys from environment variables, or generates them once and
 * stores them in MongoDB. Private keys are never committed to the repository
 * or written to logs.
 */
@Service
public class VapidKeyService {

    private static final Logger log = LoggerFactory.getLogger(VapidKeyService.class);
    private static final int PRIVATE_KEY_LENGTH = 32;

    private final MidaProperties properties;
    private final AppSettingRepository settingRepository;

    private volatile PushService pushService;
    private volatile String publicKeyBase64;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public VapidKeyService(MidaProperties properties, AppSettingRepository settingRepository) {
        this.properties = properties;
        this.settingRepository = settingRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            ensureInitialized();
        } catch (RuntimeException ex) {
            log.error("VAPID non inizializzate; le notifiche push non saranno disponibili: {}", ex.getMessage());
        }
    }

    public synchronized void ensureInitialized() {
        if (pushService != null) {
            return;
        }
        try {
            MidaProperties.Notifications config = properties.getNotifications();
            String configuredPublic = config.getVapidPublicKey();
            String configuredPrivate = config.getVapidPrivateKey();
            String publicKey;
            String privateKey;

            boolean hasPublic = StringUtils.hasText(configuredPublic);
            boolean hasPrivate = StringUtils.hasText(configuredPrivate);
            if (hasPublic != hasPrivate) {
                throw new IllegalStateException("VAPID_PUBLIC_KEY e VAPID_PRIVATE_KEY devono essere impostate entrambe o nessuna");
            }

            if (hasPublic) {
                publicKey = configuredPublic.strip();
                privateKey = configuredPrivate.strip();
            } else {
                AppSetting stored = settingRepository.findById(AppSetting.VAPID_ID).orElse(null);
                if (stored != null
                        && StringUtils.hasText(stored.getPublicKey())
                        && StringUtils.hasText(stored.getPrivateKey())) {
                    publicKey = stored.getPublicKey();
                    privateKey = stored.getPrivateKey();
                } else {
                    GeneratedKeys generated = generateKeys();
                    publicKey = generated.publicKey();
                    privateKey = generated.privateKey();
                    AppSetting setting = new AppSetting();
                    setting.setId(AppSetting.VAPID_ID);
                    setting.setPublicKey(publicKey);
                    setting.setPrivateKey(privateKey);
                    setting.setCreatedAt(Instant.now());
                    settingRepository.save(setting);
                    log.info("Generate nuove chiavi VAPID e salvate in MongoDB (collezione appSettings)");
                }
            }

            KeyPair keyPair = new KeyPair(
                    Utils.loadPublicKey(publicKey),
                    Utils.loadPrivateKey(privateKey));
            if (!Utils.verifyKeyPair(keyPair.getPrivate(), keyPair.getPublic())) {
                throw new IllegalStateException("La coppia di chiavi VAPID non è valida");
            }
            PushService service = new PushService(keyPair, config.getVapidSubject());
            this.publicKeyBase64 = publicKey;
            this.pushService = service;
        } catch (Exception ex) {
            throw new IllegalStateException("Impossibile inizializzare VAPID: " + ex.getMessage(), ex);
        }
    }

    public String publicKey() {
        ensureInitialized();
        return publicKeyBase64;
    }

    public PushService pushService() {
        ensureInitialized();
        return pushService;
    }

    public boolean isAvailable() {
        try {
            ensureInitialized();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private GeneratedKeys generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("prime256v1");
        generator.initialize(spec, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        ECPrivateKey privateKey = (ECPrivateKey) keyPair.getPrivate();
        byte[] publicBytes = publicKey.getQ().getEncoded(false);
        byte[] privateBytes = toFixedLength(privateKey.getD(), PRIVATE_KEY_LENGTH);
        return new GeneratedKeys(base64Url(publicBytes), base64Url(privateBytes));
    }

    private byte[] toFixedLength(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        if (raw.length > length) {
            return Arrays.copyOfRange(raw, raw.length - length, raw.length);
        }
        byte[] padded = new byte[length];
        System.arraycopy(raw, 0, padded, length - raw.length, raw.length);
        return padded;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private record GeneratedKeys(String publicKey, String privateKey) {
    }
}
