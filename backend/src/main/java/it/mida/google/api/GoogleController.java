package it.mida.google.api;

import it.mida.google.GoogleClientCredentialsService;
import it.mida.google.GoogleConnection;
import it.mida.google.GoogleConnectionService;
import it.mida.google.GoogleSettings;
import it.mida.google.GoogleSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/google")
@Tag(name = "Google Classroom", description = "Connessione OAuth e sincronizzazione Classroom")
public class GoogleController {

    private static final Logger log = LoggerFactory.getLogger(GoogleController.class);

    private final GoogleConnectionService connectionService;
    private final GoogleSyncService syncService;
    private final it.mida.google.email.GmailSyncService gmailSyncService;
    private final GoogleClientCredentialsService clientCredentials;

    public GoogleController(
            GoogleConnectionService connectionService,
            GoogleSyncService syncService,
            it.mida.google.email.GmailSyncService gmailSyncService,
            GoogleClientCredentialsService clientCredentials) {
        this.connectionService = connectionService;
        this.syncService = syncService;
        this.gmailSyncService = gmailSyncService;
        this.clientCredentials = clientCredentials;
    }

    @GetMapping("/client")
    @Operation(summary = "Stato del JSON client OAuth importato")
    public GoogleClientStatusDto clientStatus() {
        return clientCredentials.status();
    }

    @PostMapping("/client")
    @Operation(summary = "Importa il JSON del client OAuth scaricato da Google Cloud")
    public GoogleClientStatusDto importClient(
            @Valid @RequestBody ImportGoogleClientRequest request) {
        // Le credenziali precedenti non valgono più: elimina la connessione
        // OAuth prima di sostituire il client.
        connectionService.disconnect();
        return clientCredentials.importJson(request.json());
    }

    @DeleteMapping("/client")
    @Operation(summary = "Rimuove il JSON client OAuth e scollega l'account")
    public ResponseEntity<Void> deleteClient() {
        connectionService.disconnect();
        clientCredentials.delete();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/status")
    @Operation(summary = "Stato della connessione Classroom")
    public GoogleStatusDto status() {
        GoogleSettings settings = connectionService.settings();
        return connectionService.connection()
                .map(connection -> toStatus(connection, settings))
                .orElseGet(() -> toStatus(null, settings));
    }

    @GetMapping("/connect")
    @Operation(summary = "Avvia il flusso OAuth con Google")
    public ResponseEntity<Void> connect() {
        if (!connectionService.configured()) {
            return redirect("not-configured", null);
        }
        try {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(connectionService.connectUrl()))
                    .build();
        } catch (RuntimeException ex) {
            log.warn("Avvio OAuth Google non riuscito: {}", ex.getMessage());
            return redirect("error", ex.getMessage());
        }
    }

    @GetMapping("/oauth/callback")
    @Operation(summary = "Callback OAuth Google")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription) {
        if (error != null) {
            return redirect("error", errorDescription == null ? error : errorDescription);
        }
        try {
            connectionService.handleCallback(code, state);
            gmailSyncService.syncAsync();
            syncService.syncAsync();
            return redirect("connected", null);
        } catch (RuntimeException ex) {
            log.warn("Callback OAuth Google non riuscito: {}", ex.getMessage());
            return redirect("error", ex.getMessage());
        }
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Scollega l'account Google")
    public ResponseEntity<Void> disconnect() {
        connectionService.disconnect();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync")
    @Operation(summary = "Avvia una sincronizzazione Classroom in background")
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

    @PutMapping("/settings")
    @Operation(summary = "Aggiorna ricerca Gmail e sincronizzazioni Google")
    public GoogleStatusDto updateSettings(
            @Valid @RequestBody UpdateGoogleSettingsRequest request) {
        GoogleSettings current = connectionService.settings();
        GoogleSettings updated = connectionService.updateSettings(
                request.gmailQuery() == null ? current.getGmailQuery() : request.gmailQuery(),
                request.gmailEnabled() == null ? current.isGmailEnabled() : request.gmailEnabled(),
                request.classroomEnabled() == null
                        ? current.isClassroomEnabled()
                        : request.classroomEnabled());
        return connectionService.connection()
                .map(connection -> toStatus(connection, updated))
                .orElseGet(() -> toStatus(null, updated));
    }

    private GoogleStatusDto toStatus(GoogleConnection connection, GoogleSettings settings) {
        List<String> scopes = connection == null || connection.getScopes() == null
                ? List.of()
                : Arrays.stream(connection.getScopes().split("\\s+"))
                        .filter(value -> !value.isBlank())
                        .toList();
        return new GoogleStatusDto(
                connectionService.configured(),
                connection != null,
                connection == null ? null : connectionService.accountEmail(connection),
                connection == null ? null : connection.getConnectedAt(),
                scopes,
                connection == null ? null : connection.getLastSyncAt(),
                connection == null || connection.getLastSyncStatus() == null
                        ? "NEVER"
                        : connection.getLastSyncStatus(),
                connection == null ? null : connection.getLastError(),
                syncService.isRunning(),
                settings.isClassroomEnabled(),
                connection == null ? 0 : connection.getLastClassroomItemCount(),
                connection == null ? 0 : connection.getLastNewClassroomItemCount(),
                settings.getUpdatedAt());
    }

    private ResponseEntity<Void> redirect(String result, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/impostazioni")
                .queryParam("google", result);
        if (message != null && !message.isBlank()) {
            builder.queryParam("message", message);
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(builder.build().encode().toUri())
                .build();
    }
}
