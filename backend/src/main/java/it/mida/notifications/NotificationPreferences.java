package it.mida.notifications;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "notificationPreferences")
public class NotificationPreferences {

    public static final String DEFAULT_ID = "default";

    @Id
    private String id = DEFAULT_ID;

    private boolean enabled = true;

    private boolean newAssignmentsEnabled = true;

    private boolean remindersEnabled = true;

    private int reminderDaysBefore = 1;

    /** Stored as HH:mm, in the configured Europe/Rome time zone. */
    private String reminderTime = "18:00";

    private String timezone = "Europe/Rome";

    private Instant updatedAt;

    public static NotificationPreferences defaults() {
        NotificationPreferences preferences = new NotificationPreferences();
        preferences.setId(DEFAULT_ID);
        preferences.setUpdatedAt(Instant.now());
        return preferences;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNewAssignmentsEnabled() {
        return newAssignmentsEnabled;
    }

    public void setNewAssignmentsEnabled(boolean newAssignmentsEnabled) {
        this.newAssignmentsEnabled = newAssignmentsEnabled;
    }

    public boolean isRemindersEnabled() {
        return remindersEnabled;
    }

    public void setRemindersEnabled(boolean remindersEnabled) {
        this.remindersEnabled = remindersEnabled;
    }

    public int getReminderDaysBefore() {
        return reminderDaysBefore;
    }

    public void setReminderDaysBefore(int reminderDaysBefore) {
        this.reminderDaysBefore = reminderDaysBefore;
    }

    public String getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(String reminderTime) {
        this.reminderTime = reminderTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
