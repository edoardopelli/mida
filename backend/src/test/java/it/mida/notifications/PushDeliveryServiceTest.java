package it.mida.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushDeliveryServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    @Mock
    private WebPushSender sender;

    private PushDeliveryService service;

    @BeforeEach
    void setUp() {
        service = new PushDeliveryService(subscriptionRepository, deliveryRepository, sender);
    }

    @Test
    void skipsAnAlreadyDeliveredNotificationAfterRestart() {
        PushSubscription first = subscription("s1");
        PushSubscription second = subscription("s2");
        when(deliveryRepository.existsByDedupKey("due-reminder:2026-11-14:s1")).thenReturn(true);
        when(deliveryRepository.existsByDedupKey("due-reminder:2026-11-14:s2")).thenReturn(false);
        when(deliveryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.send(any(), any())).thenReturn(WebPushSender.SendResult.success());

        PushDeliveryService.DeliverySummary summary = service.deliver(
                List.of(first, second),
                "due-reminder:2026-11-14",
                NotificationDelivery.Type.DUE_REMINDER,
                "MIDA — Compiti per domani",
                "Hai 1 compito da consegnare domani.",
                "/calendario?date=2026-11-14",
                LocalDate.of(2026, 11, 14),
                null,
                "mida-due-2026-11-14");

        assertThat(summary.sent()).isEqualTo(1);
        assertThat(summary.subscriptions()).isEqualTo(2);
        verify(sender).send(any(), any());
    }

    @Test
    void deactivatesAnExpiredSubscriptionAndRecordsTheFailure() {
        PushSubscription expired = subscription("s1");
        when(deliveryRepository.existsByDedupKey(any())).thenReturn(false);
        ArgumentCaptor<NotificationDelivery> deliveryCaptor = ArgumentCaptor.forClass(NotificationDelivery.class);
        when(deliveryRepository.save(deliveryCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sender.send(any(), any()))
                .thenReturn(WebPushSender.SendResult.expired("Endpoint push scaduto (HTTP 410)"));

        PushDeliveryService.DeliverySummary summary = service.deliver(
                List.of(expired),
                "test:uuid",
                NotificationDelivery.Type.TEST,
                "MIDA — Notifica di prova",
                "Test",
                "/",
                LocalDate.of(2026, 11, 13),
                null,
                "mida-test");

        assertThat(summary.expired()).isEqualTo(1);
        assertThat(expired.isActive()).isFalse();
        assertThat(expired.getLastFailureAt()).isNotNull();
        assertThat(deliveryCaptor.getValue().getStatus()).isEqualTo(NotificationDelivery.Status.EXPIRED);
        verify(subscriptionRepository).save(expired);
    }

    @Test
    void doesNotSendWhenTheDeliveryCannotBeRecorded() {
        when(deliveryRepository.existsByDedupKey(any())).thenReturn(false);
        when(deliveryRepository.save(any())).thenThrow(new RuntimeException("Mongo down"));

        PushDeliveryService.DeliverySummary summary = service.deliver(
                List.of(subscription("s1")),
                "test:uuid",
                NotificationDelivery.Type.TEST,
                "Titolo",
                "Test",
                "/",
                LocalDate.of(2026, 11, 13),
                null,
                "mida-test");

        assertThat(summary.failed()).isEqualTo(1);
        verify(sender, never()).send(any(), any());
    }

    private PushSubscription subscription(String id) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setEndpoint("https://fcm.googleapis.com/push/" + id);
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscription.setActive(true);
        subscription.setCreatedAt(Instant.now());
        return subscription;
    }
}
