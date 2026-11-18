package it.mida.notifications;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class WebPushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final VapidKeyService vapidKeyService;
    private final ObjectMapper objectMapper;

    public WebPushSender(VapidKeyService vapidKeyService, ObjectMapper objectMapper) {
        this.vapidKeyService = vapidKeyService;
        this.objectMapper = objectMapper;
    }

    public synchronized SendResult send(PushSubscription subscription, PushPayload payload) {
        try {
            PushService pushService = vapidKeyService.pushService();
            String json = objectMapper.writeValueAsString(Map.of(
                    "title", payload.title(),
                    "body", payload.body(),
                    "url", payload.url(),
                    "tag", payload.tag(),
                    "icon", "/icons/icon-192.png",
                    "badge", "/icons/badge-72.png",
                    "data", payload.data() == null ? Map.of() : payload.data()));
            Notification notification = Notification.builder()
                    .endpoint(subscription.getEndpoint())
                    .userPublicKey(subscription.getP256dh())
                    .userAuth(subscription.getAuth())
                    .payload(json.getBytes(StandardCharsets.UTF_8))
                    .ttl(86400)
                    .topic(payload.tag())
                    .build();
            HttpResponse response = pushService.send(notification, Encoding.AES128GCM);
            int status = response.getStatusLine().getStatusCode();
            if (status == 201 || status == 202) {
                return SendResult.success();
            }
            if (status == 404 || status == 410) {
                String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
                log.warn("Endpoint push scaduto per {}: HTTP {}{}",
                        maskEndpoint(subscription.getEndpoint()), status,
                        body.isBlank() ? "" : " - " + body);
                return SendResult.expired("Endpoint push scaduto (HTTP " + status + ")");
            }
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity());
            String error = "HTTP " + status + (body.isBlank() ? "" : ": " + body);
            log.warn("Invio push fallito per {}: {}", maskEndpoint(subscription.getEndpoint()), error);
            return SendResult.failed(error);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SendResult.failed("Invio interrotto");
        } catch (Exception ex) {
            log.warn("Errore invio push: {}", ex.getMessage());
            return SendResult.failed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() < 40) {
            return "endpoint";
        }
        return endpoint.substring(0, 24) + "…" + endpoint.substring(endpoint.length() - 8);
    }

    public record PushPayload(
            String title,
            String body,
            String url,
            String tag,
            Map<String, Object> data) {
    }

    public record SendResult(Status status, String error) {

        public enum Status {
            SUCCESS,
            EXPIRED,
            FAILED
        }

        static SendResult success() {
            return new SendResult(Status.SUCCESS, null);
        }

        static SendResult expired(String error) {
            return new SendResult(Status.EXPIRED, error);
        }

        static SendResult failed(String error) {
            return new SendResult(Status.FAILED, error);
        }
    }
}
