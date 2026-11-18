package it.mida.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.mida.config.MidaProperties;
import it.mida.homework.HomeworkQueryService;
import it.mida.homework.api.HomeworkQuery;
import it.mida.homework.api.HomeworkView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Mock
    private NotificationService notificationService;

    @Mock
    private HomeworkQueryService homeworkQueryService;

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private PushDeliveryService deliveryService;

    private MidaProperties properties;
    private ReminderService service;
    private NotificationPreferences preferences;

    @BeforeEach
    void setUp() {
        properties = new MidaProperties();
        properties.getNotifications().setEnabled(true);
        preferences = NotificationPreferences.defaults();
        preferences.setReminderDaysBefore(1);
        preferences.setReminderTime("18:00");
        preferences.setTimezone("Europe/Rome");
        when(notificationService.currentPreferences()).thenReturn(preferences);
        service = new ReminderService(
                properties,
                notificationService,
                homeworkQueryService,
                subscriptionRepository,
                deliveryService);
    }

    @Test
    void sendsOneReminderForTheNextDayAndFiltersCompletedAssignments() {
        LocalDate target = LocalDate.of(2026, 11, 14);
        when(homeworkQueryService.calendar(any())).thenReturn(List.of(
                view("Matematica", target),
                view("Storia", target),
                view("Italiano", target)));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(subscription()));
        when(deliveryService.deliver(
                        anyList(), anyString(), eq(NotificationDelivery.Type.DUE_REMINDER),
                        anyString(), anyString(), anyString(), eq(target), eq(null), anyString()))
                .thenReturn(new PushDeliveryService.DeliverySummary(1, 0, 0, 1));

        ReminderService.ReminderRun result = service.runAt(
                ZonedDateTime.of(2026, 11, 13, 18, 15, 0, 0, ROME));

        ArgumentCaptor<HomeworkQuery> query = ArgumentCaptor.forClass(HomeworkQuery.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(homeworkQueryService).calendar(query.capture());
        assertThat(query.getValue().from()).isEqualTo(target);
        assertThat(query.getValue().to()).isEqualTo(target);
        assertThat(query.getValue().completed()).isFalse();
        verify(deliveryService).deliver(
                anyList(),
                eq("due-reminder:2026-11-14"),
                eq(NotificationDelivery.Type.DUE_REMINDER),
                eq("MIDA — Compiti per domani"),
                body.capture(),
                eq("/calendario?view=listMonth&date=2026-11-14"),
                eq(target),
                eq(null),
                eq("mida-due-2026-11-14"));
        assertThat(body.getValue())
                .contains("Hai 3 compiti da consegnare domani:")
                .contains("• Matematica: Descrizione")
                .contains("• Storia: Descrizione")
                .contains("• Italiano: Descrizione");
        assertThat(result.assignments()).isEqualTo(3);
        assertThat(result.sent()).isEqualTo(1);
    }

    @Test
    void linksDirectlyToTheAssignmentWhenOnlyOneIsDue() {
        LocalDate target = LocalDate.of(2026, 11, 14);
        String longDescription = "Leggere il capitolo " + "molto lungo ".repeat(20);
        when(homeworkQueryService.calendar(any()))
                .thenReturn(List.of(view("Italiano", target, longDescription)));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(subscription()));
        when(deliveryService.deliver(
                        anyList(), anyString(), eq(NotificationDelivery.Type.DUE_REMINDER),
                        anyString(), anyString(), anyString(), eq(target), eq(null), anyString()))
                .thenReturn(new PushDeliveryService.DeliverySummary(1, 0, 0, 1));

        service.runAt(ZonedDateTime.of(2026, 11, 13, 18, 15, 0, 0, ROME));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(deliveryService).deliver(
                anyList(),
                eq("due-reminder:2026-11-14"),
                eq(NotificationDelivery.Type.DUE_REMINDER),
                eq("MIDA — Compiti per domani"),
                body.capture(),
                eq("/calendario?view=listMonth&date=2026-11-14&assignment=assignment-Italiano"),
                eq(target),
                eq(null),
                eq("mida-due-2026-11-14"));
        assertThat(body.getValue())
                .contains("Hai 1 compito da consegnare domani:")
                .contains("• Italiano: Leggere il capitolo")
                .contains("…");
        String bullet = body.getValue().lines()
                .filter(line -> line.startsWith("•"))
                .findFirst()
                .orElse("");
        assertThat(bullet.length()).isLessThan(120);
    }

    @Test
    void skipsTheRunBeforeTheConfiguredTime() {
        ReminderService.ReminderRun result = service.runAt(
                ZonedDateTime.of(2026, 11, 13, 17, 59, 0, 0, ROME));

        assertThat(result).isEqualTo(new ReminderService.ReminderRun(0, 0, 0, 0));
        verify(homeworkQueryService, never()).calendar(any());
        verify(deliveryService, never()).deliver(
                anyList(), anyString(), any(), anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    void skipsTheRunWhenRemindersAreDisabled() {
        preferences.setRemindersEnabled(false);

        ReminderService.ReminderRun result = service.runAt(
                ZonedDateTime.of(2026, 11, 13, 20, 0, 0, 0, ROME));

        assertThat(result).isEqualTo(new ReminderService.ReminderRun(0, 0, 0, 0));
        verify(homeworkQueryService, never()).calendar(any());
    }

    @Test
    void skipsTheRunWhenNoAssignmentIsDue() {
        when(homeworkQueryService.calendar(any())).thenReturn(List.of());

        ReminderService.ReminderRun result = service.runAt(
                ZonedDateTime.of(2026, 11, 13, 18, 30, 0, 0, ROME));

        assertThat(result).isEqualTo(new ReminderService.ReminderRun(0, 0, 0, 0));
        verify(subscriptionRepository, never()).findByActiveTrue();
    }

    private HomeworkView view(String subject, LocalDate dueDate) {
        return view(subject, dueDate, "Descrizione");
    }

    private HomeworkView view(String subject, LocalDate dueDate, String description) {
        return new HomeworkView(
                "assignment-" + subject,
                "import-1",
                "ABC123",
                "compiti.txt",
                Instant.parse("2026-11-13T08:00:00Z"),
                LocalDate.of(2026, 11, 12),
                "12 novembre 2026",
                dueDate,
                "14 novembre 2026",
                subject,
                "Docente",
                description,
                false,
                null,
                1,
                Instant.parse("2026-11-13T08:00:00Z"));
    }

    private PushSubscription subscription() {
        PushSubscription subscription = new PushSubscription();
        subscription.setId("subscription-1");
        subscription.setEndpoint("https://fcm.googleapis.com/push/1");
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscription.setActive(true);
        return subscription;
    }
}
