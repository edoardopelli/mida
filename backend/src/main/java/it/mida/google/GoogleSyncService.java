package it.mida.google;

import it.mida.events.MidaEventPublisher;
import it.mida.events.MidaEvents;
import it.mida.google.classroom.ClassroomSyncService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sincronizza Google Classroom. Non ha un polling proprio: viene avviata
 * dopo il collegamento OAuth, manualmente, oppure da {@code ImapSyncService}
 * quando arriva almeno una nuova email. La posta è gestita via Gmail API.
 */
@Service
public class GoogleSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSyncService.class);
    private static final int MAX_ERROR_LENGTH = 800;

    private final GoogleConnectionService connectionService;
    private final ClassroomSyncService classroomSyncService;
    private final MidaEventPublisher events;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GoogleSyncService(
            GoogleConnectionService connectionService,
            ClassroomSyncService classroomSyncService,
            MidaEventPublisher events) {
        this.connectionService = connectionService;
        this.classroomSyncService = classroomSyncService;
        this.events = events;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Async
    public void syncAsync() {
        sync();
    }

    public void sync() {
        if (!connectionService.configured() || connectionService.connection().isEmpty()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            GoogleConnection connection = connectionService.requireConnection();
            connection.setLastSyncStatus("SYNCING");
            connection.setLastError(null);
            connectionService.save(connection);

            String accessToken = connectionService.accessToken();
            GoogleSettings settings = connectionService.settings();
            ClassroomSyncService.SyncResult classroom = settings.isClassroomEnabled()
                    ? classroomSyncService.sync(accessToken, settings)
                    : ClassroomSyncService.SyncResult.empty();

            connection = connectionService.requireConnection();
            connection.setLastSyncAt(Instant.now());
            connection.setLastClassroomItemCount(classroom.seen());
            connection.setLastNewClassroomItemCount(classroom.created());
            connection.setLastSyncStatus(classroom.errors() == 0 ? "OK" : "ERROR");
            connection.setLastError(truncate(classroom.lastError()));
            connectionService.save(connection);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("classroomItems", classroom.seen());
            payload.put("newClassroomItems", classroom.created());
            payload.put("classroomMaterials", classroom.materials());
            payload.put("newClassroomMaterials", classroom.newMaterials());
            payload.put("errors", classroom.errors());
            events.publish(MidaEvents.GOOGLE_SYNC, payload);
            log.info(
                    "Sincronizzazione Classroom completata: {} elementi ({} nuovi), {} errori",
                    classroom.seen(), classroom.created(), classroom.errors());
        } catch (GoogleReauthRequiredException ex) {
            markFailure("REAUTH_REQUIRED", ex.getMessage());
            log.warn("Sincronizzazione Classroom sospesa: {}", ex.getMessage());
        } catch (GoogleNotConfiguredException ex) {
            markFailure("ERROR", ex.getMessage());
        } catch (RuntimeException ex) {
            markFailure("ERROR", ex.getMessage());
            log.error("Sincronizzazione Classroom non riuscita", ex);
        } finally {
            running.set(false);
        }
    }

    private void markFailure(String status, String message) {
        try {
            GoogleConnection connection = connectionService.requireConnection();
            connection.setLastSyncAt(Instant.now());
            connection.setLastSyncStatus(status);
            connection.setLastError(truncate(message));
            connectionService.save(connection);
        } catch (RuntimeException ex) {
            log.warn("Impossibile aggiornare lo stato Classroom: {}", ex.getMessage());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH) + "…";
    }
}
