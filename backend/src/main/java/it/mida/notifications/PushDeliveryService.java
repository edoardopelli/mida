package it.mida.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Persists one delivery record before sending. The unique dedup key makes the
 * notification pipeline idempotent across restarts and concurrent schedulers.
 */
@Service
public class PushDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PushDeliveryService.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final WebPushSender sender;

    public PushDeliveryService(
            PushSubscriptionRepository subscriptionRepository,
            NotificationDeliveryRepository deliveryRepository,
            WebPushSender sender) {
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.sender = sender;
    }

    public DeliverySummary deliver(
            List<PushSubscription> subscriptions,
            String dedupPrefix,
            NotificationDelivery.Type type,
            String title,
            String body,
            String url,
            LocalDate targetDate,
            String importId,
            String tag) {
        int sent = 0;
        int failed = 0;
        int expired = 0;
        for (PushSubscription subscription : subscriptions) {
            String dedupKey = dedupPrefix + ":" + subscription.getId();
            if (deliveryRepository.existsByDedupKey(dedupKey)) {
                continue;
            }
            NotificationDelivery delivery = new NotificationDelivery();
            delivery.setDedupKey(dedupKey);
            delivery.setType(type);
            delivery.setSubscriptionId(subscription.getId());
            delivery.setEndpoint(subscription.getEndpoint());
            delivery.setImportId(importId);
            delivery.setTargetDate(targetDate);
            delivery.setTitle(title);
            delivery.setBody(body);
            delivery.setStatus(NotificationDelivery.Status.PENDING);
            delivery.setCreatedAt(Instant.now());
            delivery.setAttempts(1);
            try {
                delivery = deliveryRepository.save(delivery);
            } catch (DuplicateKeyException ex) {
                continue;
            } catch (RuntimeException ex) {
                log.warn("Impossibile registrare la consegna push: {}", ex.getMessage());
                failed++;
                continue;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", type.name());
            if (targetDate != null) {
                data.put("date", targetDate.toString());
            }
            if (importId != null) {
                data.put("importId", importId);
            }
            WebPushSender.SendResult result = sender.send(
                    subscription,
                    new WebPushSender.PushPayload(title, body, url, tag, data));

            if (result.status() == WebPushSender.SendResult.Status.SUCCESS) {
                sent++;
                delivery.setStatus(NotificationDelivery.Status.SENT);
                delivery.setSentAt(Instant.now());
                delivery.setError(null);
                subscription.setLastSuccessAt(Instant.now());
            } else if (result.status() == WebPushSender.SendResult.Status.EXPIRED) {
                expired++;
                delivery.setStatus(NotificationDelivery.Status.EXPIRED);
                delivery.setError(result.error());
                subscription.setActive(false);
                subscription.setLastFailureAt(Instant.now());
            } else {
                failed++;
                delivery.setStatus(NotificationDelivery.Status.FAILED);
                delivery.setError(truncate(result.error()));
                subscription.setLastFailureAt(Instant.now());
            }
            try {
                deliveryRepository.save(delivery);
                subscriptionRepository.save(subscription);
            } catch (RuntimeException ex) {
                log.warn("Impossibile aggiornare lo stato della consegna push: {}", ex.getMessage());
            }
        }
        return new DeliverySummary(sent, failed, expired, subscriptions.size());
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    public record DeliverySummary(int sent, int failed, int expired, int subscriptions) {
    }
}
