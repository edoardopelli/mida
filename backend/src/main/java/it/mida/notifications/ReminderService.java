package it.mida.notifications;

import it.mida.config.MidaProperties;
import it.mida.homework.HomeworkQueryService;
import it.mida.homework.api.HomeworkQuery;
import it.mida.homework.api.HomeworkView;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends due-date reminders once per day and subscription. Deduplication is
 * persisted in notificationDeliveries, so a backend restart cannot duplicate
 * a reminder.
 */
@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_NOTIFICATION_ASSIGNMENTS = 6;
    private static final int MAX_DESCRIPTION_LENGTH = 90;

    private final MidaProperties properties;
    private final NotificationService notificationService;
    private final HomeworkQueryService homeworkQueryService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushDeliveryService deliveryService;

    public ReminderService(
            MidaProperties properties,
            NotificationService notificationService,
            HomeworkQueryService homeworkQueryService,
            PushSubscriptionRepository subscriptionRepository,
            PushDeliveryService deliveryService) {
        this.properties = properties;
        this.notificationService = notificationService;
        this.homeworkQueryService = homeworkQueryService;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryService = deliveryService;
    }

    @Scheduled(cron = "${mida.notifications.reminder-cron:0 * * * * *}", zone = "Europe/Rome")
    public void scheduledCheck() {
        try {
            runAt(ZonedDateTime.now(ZoneId.of("Europe/Rome")));
        } catch (RuntimeException ex) {
            log.warn("Controllo promemoria non riuscito: {}", ex.getMessage());
        }
    }

    public ReminderRun runAt(ZonedDateTime now) {
        if (!properties.getNotifications().isEnabled()) {
            return new ReminderRun(0, 0, 0, 0);
        }
        NotificationPreferences preferences = notificationService.currentPreferences();
        if (!preferences.isEnabled() || !preferences.isRemindersEnabled()) {
            return new ReminderRun(0, 0, 0, 0);
        }

        ZoneId zone;
        try {
            zone = ZoneId.of(preferences.getTimezone());
        } catch (RuntimeException ex) {
            zone = properties.getTimeZone();
        }
        ZonedDateTime localNow = now.withZoneSameInstant(zone);
        LocalTime reminderTime;
        try {
            reminderTime = LocalTime.parse(preferences.getReminderTime(), TIME_FORMAT);
        } catch (DateTimeParseException ex) {
            reminderTime = LocalTime.of(18, 0);
        }
        if (localNow.toLocalTime().isBefore(reminderTime)) {
            return new ReminderRun(0, 0, 0, 0);
        }

        LocalDate targetDate = localNow.toLocalDate().plusDays(preferences.getReminderDaysBefore());
        HomeworkQuery query = new HomeworkQuery(
                targetDate,
                targetDate,
                HomeworkQuery.DateField.DUE,
                null,
                null,
                false,
                null,
                0,
                100);
        List<HomeworkView> due = homeworkQueryService.calendar(query);
        if (due.isEmpty()) {
            return new ReminderRun(0, 0, 0, 0);
        }
        List<PushSubscription> subscriptions = subscriptionRepository.findByActiveTrue();
        if (subscriptions.isEmpty()) {
            return new ReminderRun(0, 0, 0, 0);
        }

        String when = switch (preferences.getReminderDaysBefore()) {
            case 0 -> "oggi";
            case 1 -> "domani";
            default -> "tra " + preferences.getReminderDaysBefore() + " giorni";
        };
        String title = switch (preferences.getReminderDaysBefore()) {
            case 0 -> "MIDA — Compiti per oggi";
            case 1 -> "MIDA — Compiti per domani";
            default -> "MIDA — Compiti in scadenza";
        };
        // Lo stesso compito può comparire più volte se il calendario restituisce
        // righe duplicate: la notifica elenca ogni attività una sola volta.
        Map<String, HomeworkView> unique = new LinkedHashMap<>();
        due.forEach(item -> unique.putIfAbsent(
                item.id() == null ? item.subject() + "|" + item.description() : item.id(),
                item));
        List<HomeworkView> assignments = List.copyOf(unique.values());
        int visible = Math.min(assignments.size(), MAX_NOTIFICATION_ASSIGNMENTS);
        StringBuilder body = new StringBuilder();
        body.append("Hai ").append(assignments.size())
                .append(assignments.size() == 1 ? " compito" : " compiti")
                .append(" da consegnare ").append(when).append(":\n\n");
        for (int index = 0; index < visible; index++) {
            HomeworkView item = assignments.get(index);
            body.append("• ").append(item.subject());
            String detail = preview(item.description(), MAX_DESCRIPTION_LENGTH);
            if (StringUtils.hasText(detail)) {
                body.append(": ").append(detail);
            }
            body.append('\n');
        }
        if (assignments.size() > visible) {
            body.append("… e altri ").append(assignments.size() - visible)
                    .append(assignments.size() - visible == 1 ? " compito" : " compiti")
                    .append('\n');
        }
        String bodyText = body.toString().stripTrailing();

        String url = "/calendario?view=listMonth&date=" + targetDate;
        if (assignments.size() == 1 && assignments.getFirst().id() != null) {
            url += "&assignment=" + assignments.getFirst().id();
        }

        PushDeliveryService.DeliverySummary summary = deliveryService.deliver(
                subscriptions,
                "due-reminder:" + targetDate,
                NotificationDelivery.Type.DUE_REMINDER,
                title,
                bodyText,
                url,
                targetDate,
                null,
                "mida-due-" + targetDate);
        if (summary.sent() > 0) {
            log.info("Promemoria per {} inviato a {} sottoscrizioni", targetDate, summary.sent());
        }
        return new ReminderRun(assignments.size(), summary.sent(), summary.failed(), summary.expired());
    }

    private String preview(String description, int maxLength) {
        if (!StringUtils.hasText(description)) {
            return "";
        }
        String firstLine = description.lines()
                .map(String::strip)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("")
                .replaceAll("\\s+", " ");
        if (firstLine.length() <= maxLength) {
            return firstLine;
        }
        return firstLine.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    public record ReminderRun(int assignments, int sent, int failed, int expired) {
    }
}
