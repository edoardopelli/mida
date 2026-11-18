package it.mida.google;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Impostazioni condivise dell'integrazione Google: ricerca Gmail e
 * abilitazione Classroom.
 */
@Document(collection = "googleSettings")
public class GoogleSettings {

    public static final String DEFAULT_ID = "default";

    @Id
    private String id = DEFAULT_ID;

    private String gmailQuery = "from:didup";

    private boolean gmailEnabled = true;

    private boolean classroomEnabled = true;

    private Instant updatedAt;

    public static GoogleSettings defaults() {
        return new GoogleSettings();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGmailQuery() {
        return gmailQuery;
    }

    public void setGmailQuery(String gmailQuery) {
        this.gmailQuery = gmailQuery;
    }

    public boolean isGmailEnabled() {
        return gmailEnabled;
    }

    public void setGmailEnabled(boolean gmailEnabled) {
        this.gmailEnabled = gmailEnabled;
    }

    public boolean isClassroomEnabled() {
        return classroomEnabled;
    }

    public void setClassroomEnabled(boolean classroomEnabled) {
        this.classroomEnabled = classroomEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
