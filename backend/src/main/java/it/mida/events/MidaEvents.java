package it.mida.events;

import it.mida.homework.api.HomeworkView;
import java.util.Map;

public final class MidaEvents {

    public static final String CONNECTED = "connected";
    public static final String IMPORT_COMPLETED = "import.completed";
    public static final String DIARY_IMPORTED = "diary.imported";
    public static final String HOMEWORK_UPDATED = "homework.updated";
    public static final String IMPORT_ISSUE = "import.issue";
    public static final String IMPORT_STATUS = "import.status";
    public static final String GOOGLE_SYNC = "google.sync";
    public static final String MAIL_SYNC = "mail.sync";

    private MidaEvents() {
    }

    public static Map<String, Object> homeworkUpdated(HomeworkView homework) {
        return Map.of(
                "assignmentId", homework.id(),
                "completed", homework.completed(),
                "subject", homework.subject(),
                "dueDate", homework.dueDate().toString());
    }
}
