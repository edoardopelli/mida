package it.mida.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.mida.config.MidaProperties;
import it.mida.homework.HomeworkAssignment;
import it.mida.homework.HomeworkAssignmentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationPreferencesRepository preferencesRepository;

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    @Mock
    private HomeworkAssignmentRepository assignmentRepository;

    @Mock
    private PushDeliveryService deliveryService;

    @Mock
    private VapidKeyService vapidKeyService;

    private MidaProperties properties;
    private NotificationService service;
    private NotificationPreferences preferences;

    @BeforeEach
    void setUp() {
        properties = new MidaProperties();
        properties.getNotifications().setEnabled(true);
        preferences = NotificationPreferences.defaults();
        when(preferencesRepository.findById(NotificationPreferences.DEFAULT_ID))
                .thenReturn(Optional.of(preferences));
        service = new NotificationService(
                properties,
                preferencesRepository,
                subscriptionRepository,
                assignmentRepository,
                deliveryService,
                vapidKeyService);
    }

    @Test
    void groupsNewAssignmentsBySubjectAndUsesStableDeduplication() {
        when(assignmentRepository.findByFirstImportId("import-1")).thenReturn(List.of(
                assignment("a1", "Matematica", "2026-11-18"),
                assignment("a2", "Storia", "2026-11-16"),
                assignment("a3", "Matematica", "2026-11-20")));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(subscription()));
        when(deliveryService.deliver(
                        anyList(), anyString(), eq(NotificationDelivery.Type.NEW_ASSIGNMENTS),
                        anyString(), anyString(), anyString(), eq(LocalDate.of(2026, 11, 16)),
                        eq("import-1"), anyString()))
                .thenReturn(new PushDeliveryService.DeliverySummary(1, 0, 0, 1));

        service.notifyNewImport("import-1");

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(deliveryService).deliver(
                anyList(),
                eq("new-assignments:import-1"),
                eq(NotificationDelivery.Type.NEW_ASSIGNMENTS),
                eq("MIDA — Nuovi compiti assegnati"),
                body.capture(),
                eq("/calendario?view=listMonth&date=2026-11-16&assignment=a2"),
                eq(LocalDate.of(2026, 11, 16)),
                eq("import-1"),
                eq("mida-new"));
        assertThat(body.getValue())
                .contains("Sono stati importati 3 compiti:")
                .contains("Matematica: 2")
                .contains("Storia: 1");
    }

    @Test
    void doesNotNotifyWhenGlobalPreferencesDisableNewAssignments() {
        preferences.setNewAssignmentsEnabled(false);

        service.notifyNewImport("import-1");

        verify(deliveryService, never()).deliver(
                anyList(), anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void doesNotNotifyWhenNoSubscriptionIsActive() {
        when(assignmentRepository.findByFirstImportId("import-1"))
                .thenReturn(List.of(assignment("a1", "Matematica", "2026-11-18")));
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        service.notifyNewImport("import-1");

        verify(deliveryService, never()).deliver(
                anyList(), anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void doesNotNotifyWhenTheImportOnlyUpdatedExistingAssignments() {
        when(assignmentRepository.findByFirstImportId("import-1")).thenReturn(List.of());

        service.notifyNewImport("import-1");

        verify(subscriptionRepository, never()).findByActiveTrue();
        verify(deliveryService, never()).deliver(
                anyList(), anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    private HomeworkAssignment assignment(String id, String subject, String dueDate) {
        HomeworkAssignment assignment = new HomeworkAssignment();
        assignment.setId(id);
        assignment.setSubject(subject);
        assignment.setTeacher("Docente");
        assignment.setDescription("Compito di " + subject);
        assignment.setAssignedDate(LocalDate.of(2026, 11, 13));
        assignment.setDueDate(LocalDate.parse(dueDate));
        return assignment;
    }

    private PushSubscription subscription() {
        PushSubscription subscription = new PushSubscription();
        subscription.setId("subscription-1");
        subscription.setEndpoint("https://fcm.googleapis.com/push/1");
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscription.setActive(true);
        subscription.setCreatedAt(Instant.now());
        return subscription;
    }
}
