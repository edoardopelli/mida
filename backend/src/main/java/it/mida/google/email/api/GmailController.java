package it.mida.google.email.api;

import it.mida.cluster.ConditionalOnMidaRole;
import it.mida.cluster.MidaRole;
import it.mida.google.GoogleConnectionService;
import it.mida.google.GoogleSettings;
import it.mida.google.email.GmailSyncService;
import it.mida.google.email.GmailSyncState;
import it.mida.google.email.GmailSyncStateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/google/gmail")
@Tag(name = "Posta Gmail", description = "Sincronizzazione Gmail tramite API OAuth")
@ConditionalOnMidaRole({MidaRole.STANDALONE, MidaRole.MASTER})
public class GmailController {

    private final GmailSyncService syncService;
    private final GmailSyncStateRepository stateRepository;
    private final GoogleConnectionService connectionService;

    public GmailController(
            GmailSyncService syncService,
            GmailSyncStateRepository stateRepository,
            GoogleConnectionService connectionService) {
        this.syncService = syncService;
        this.stateRepository = stateRepository;
        this.connectionService = connectionService;
    }

    @GetMapping("/status")
    @Operation(summary = "Stato della sincronizzazione Gmail")
    public GmailStatusDto status() {
        GoogleSettings settings = connectionService.settings();
        GmailSyncState state = stateRepository.findById(GmailSyncState.DEFAULT_ID)
                .orElseGet(GmailSyncState::new);
        return new GmailStatusDto(
                connectionService.configured(),
                connectionService.connection().isPresent(),
                settings.isGmailEnabled(),
                settings.getGmailQuery(),
                state.getLastSyncAt(),
                state.getLastSyncStatus() == null ? "NEVER" : state.getLastSyncStatus(),
                state.getLastError(),
                syncService.isRunning(),
                state.getLastFetchedCount(),
                state.getLastNewCount(),
                state.getLastAttachmentCount(),
                state.getLastNewAttachmentCount());
    }

    @PostMapping("/sync")
    @Operation(summary = "Avvia una sincronizzazione Gmail in background")
    public ResponseEntity<Map<String, Object>> sync() {
        connectionService.requireConnection();
        boolean running = syncService.isRunning();
        if (!running) {
            syncService.syncAsync();
        }
        return ResponseEntity.accepted().body(Map.of(
                "started", !running,
                "status", running ? "running" : "started"));
    }
}
