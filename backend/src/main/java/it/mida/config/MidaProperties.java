package it.mida.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "mida")
public class MidaProperties {

    /** The first year of the school year, e.g. 2026 for 2026/2027. */
    @Min(2000)
    private int schoolYearStart = 2026;

    @NotNull
    private ZoneId timeZone = ZoneId.of("Europe/Rome");

    @Valid
    private Imports imports = new Imports();

    @Valid
    private Notifications notifications = new Notifications();

    @Valid
    private Events events = new Events();

    @Valid
    private Google google = new Google();

    public int getSchoolYearStart() {
        return schoolYearStart;
    }

    public void setSchoolYearStart(int schoolYearStart) {
        this.schoolYearStart = schoolYearStart;
    }

    public ZoneId getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(ZoneId timeZone) {
        this.timeZone = timeZone;
    }

    public Imports getImports() {
        return imports;
    }

    public void setImports(Imports imports) {
        this.imports = imports;
    }

    public Notifications getNotifications() {
        return notifications;
    }

    public void setNotifications(Notifications notifications) {
        this.notifications = notifications;
    }

    public Events getEvents() {
        return events;
    }

    public void setEvents(Events events) {
        this.events = events;
    }

    public Google getGoogle() {
        return google;
    }

    public void setGoogle(Google google) {
        this.google = google;
    }

    public static class Imports {

        private boolean enabled = true;

        @NotBlank
        private String directory = "/imports";

        @Min(1000)
        private long pollIntervalMs = 5000;

        @Min(0)
        private long initialDelayMs = 3000;

        @Min(1)
        private int stabilityScans = 2;

        @Min(1)
        private long maxFileSizeBytes = 10L * 1024L * 1024L;

        private boolean deleteAfterImport = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public int getStabilityScans() {
            return stabilityScans;
        }

        public void setStabilityScans(int stabilityScans) {
            this.stabilityScans = stabilityScans;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public boolean isDeleteAfterImport() {
            return deleteAfterImport;
        }

        public void setDeleteAfterImport(boolean deleteAfterImport) {
            this.deleteAfterImport = deleteAfterImport;
        }
    }

    public static class Notifications {

        private boolean enabled = true;

        private String vapidPublicKey = "";

        private String vapidPrivateKey = "";

        private String vapidSubject = "mailto:mida@localhost";

        private String reminderCron = "0 * * * * *";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVapidPublicKey() {
            return vapidPublicKey;
        }

        public void setVapidPublicKey(String vapidPublicKey) {
            this.vapidPublicKey = vapidPublicKey;
        }

        public String getVapidPrivateKey() {
            return vapidPrivateKey;
        }

        public void setVapidPrivateKey(String vapidPrivateKey) {
            this.vapidPrivateKey = vapidPrivateKey;
        }

        public String getVapidSubject() {
            return vapidSubject;
        }

        public void setVapidSubject(String vapidSubject) {
            this.vapidSubject = vapidSubject;
        }

        public String getReminderCron() {
            return reminderCron;
        }

        public void setReminderCron(String reminderCron) {
            this.reminderCron = reminderCron;
        }
    }

    public static class Events {

        @Min(5000)
        private long heartbeatMs = 25000;

        @Min(60000)
        private long emitterTimeoutMs = 30L * 60L * 1000L;

        public long getHeartbeatMs() {
            return heartbeatMs;
        }

        public void setHeartbeatMs(long heartbeatMs) {
            this.heartbeatMs = heartbeatMs;
        }

        public long getEmitterTimeoutMs() {
            return emitterTimeoutMs;
        }

        public void setEmitterTimeoutMs(long emitterTimeoutMs) {
            this.emitterTimeoutMs = emitterTimeoutMs;
        }
    }

    /**
     * Configurazione dell'integrazione Google (Gmail e Classroom). Le
     * credenziali OAuth arrivano dall'ambiente; il refresh token viene
     * conservato cifrato in MongoDB.
     */
    public static class Google {

        private boolean enabled = true;

        private boolean gmailEnabled = true;

        private String gmailQuery = "from:didup";

        @Min(1)
        @Max(500)
        private int gmailMaxResults = 50;

        @Min(60000)
        private long gmailSyncIntervalMs = 5L * 60L * 1000L;

        @Min(0)
        private long gmailInitialDelayMs = 30000;

        private boolean classroomEnabled = true;

        private boolean classroomAnnouncements = true;

        @Min(1024)
        private long maxAttachmentSizeBytes = 100L * 1024L * 1024L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isGmailEnabled() {
            return gmailEnabled;
        }

        public void setGmailEnabled(boolean gmailEnabled) {
            this.gmailEnabled = gmailEnabled;
        }

        public String getGmailQuery() {
            return gmailQuery;
        }

        public void setGmailQuery(String gmailQuery) {
            this.gmailQuery = gmailQuery;
        }

        public int getGmailMaxResults() {
            return gmailMaxResults;
        }

        public void setGmailMaxResults(int gmailMaxResults) {
            this.gmailMaxResults = gmailMaxResults;
        }

        public long getGmailSyncIntervalMs() {
            return gmailSyncIntervalMs;
        }

        public void setGmailSyncIntervalMs(long gmailSyncIntervalMs) {
            this.gmailSyncIntervalMs = gmailSyncIntervalMs;
        }

        public long getGmailInitialDelayMs() {
            return gmailInitialDelayMs;
        }

        public void setGmailInitialDelayMs(long gmailInitialDelayMs) {
            this.gmailInitialDelayMs = gmailInitialDelayMs;
        }

        public boolean isClassroomEnabled() {
            return classroomEnabled;
        }

        public void setClassroomEnabled(boolean classroomEnabled) {
            this.classroomEnabled = classroomEnabled;
        }

        public boolean isClassroomAnnouncements() {
            return classroomAnnouncements;
        }

        public void setClassroomAnnouncements(boolean classroomAnnouncements) {
            this.classroomAnnouncements = classroomAnnouncements;
        }

        public long getMaxAttachmentSizeBytes() {
            return maxAttachmentSizeBytes;
        }

        public void setMaxAttachmentSizeBytes(long maxAttachmentSizeBytes) {
            this.maxAttachmentSizeBytes = maxAttachmentSizeBytes;
        }
    }

}
