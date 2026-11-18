package it.mida.imports;

import it.mida.config.MidaProperties;
import it.mida.events.MidaEventPublisher;
import it.mida.events.MidaEvents;
import it.mida.issues.ImportIssue;
import it.mida.issues.ImportIssueService;
import it.mida.parser.DidupFileName;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reliable polling monitor for the bind-mounted Downloads directory. Polling
 * is used instead of WatchService because native file events are not reliable
 * through Docker Desktop bind mounts on macOS.
 */
@Component
public class DownloadsMonitor {

    private static final Logger log = LoggerFactory.getLogger(DownloadsMonitor.class);

    private final MidaProperties properties;
    private final ImportService importService;
    private final ImportStatusService statusService;
    private final ImportIssueService issueService;
    private final MidaEventPublisher events;
    private final MonitorState state;

    private final Map<Path, Stability> stability = new HashMap<>();

    public DownloadsMonitor(
            MidaProperties properties,
            ImportService importService,
            ImportStatusService statusService,
            ImportIssueService issueService,
            MidaEventPublisher events,
            MonitorState state) {
        this.properties = properties;
        this.importService = importService;
        this.statusService = statusService;
        this.issueService = issueService;
        this.events = events;
        this.state = state;
    }

    @Scheduled(
            fixedDelayString = "${mida.imports.poll-interval-ms:5000}",
            initialDelayString = "${mida.imports.initial-delay-ms:3000}")
    public void scheduledScan() {
        scan();
    }

    /**
     * Runs a complete scan. It is public so integration tests can invoke it
     * deterministically without waiting for the scheduler.
     */
    public void scan() {
        if (!properties.getImports().isEnabled()) {
            state.setState(MonitorState.State.DISABLED);
            state.setLastScanAt(Instant.now());
            state.setLastError(null);
            state.setDirectoryReadable(false);
            return;
        }

        MonitorState.State previousState = state.getState();
        state.setLastScanAt(Instant.now());
        Path directory = Path.of(properties.getImports().getDirectory()).toAbsolutePath().normalize();

        if (!Files.isDirectory(directory) || !Files.isReadable(directory)) {
            state.setState(MonitorState.State.ERROR);
            state.setDirectoryReadable(false);
            String message = "Directory di importazione non disponibile o non leggibile: " + directory;
            state.setLastError(message);
            log.warn(message);
            try {
                issueService.recordOnce(
                        ImportIssue.Type.DIRECTORY_UNAVAILABLE,
                        directory.toString(),
                        null,
                        null,
                        message);
            } catch (RuntimeException ex) {
                log.warn("Impossibile registrare l'anomalia della directory: {}", ex.getMessage());
            }
            publishStatusIfChanged(previousState, false);
            return;
        }

        state.setDirectoryReadable(true);
        state.setState(MonitorState.State.ACTIVE);
        try {
            issueService.resolveOpenIssues(ImportIssue.Type.DIRECTORY_UNAVAILABLE, directory.toString());
        } catch (RuntimeException ex) {
            log.warn("Impossibile risolvere l'anomalia della directory: {}", ex.getMessage());
        }

        int found = 0;
        int imported = 0;
        int errors = 0;
        String firstError = null;
        Set<Path> seen = new HashSet<>();

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path entry : stream) {
                candidates.add(entry);
            }
        } catch (IOException | SecurityException ex) {
            state.setState(MonitorState.State.ERROR);
            state.setLastError("Impossibile elencare la directory: " + ex.getMessage());
            log.error("Impossibile elencare {}", directory, ex);
            publishStatusIfChanged(previousState, false);
            return;
        }
        candidates.sort(Comparator.comparing(path -> path.getFileName().toString()));

        for (Path candidate : candidates) {
            String filename = candidate.getFileName().toString();
            Optional<DidupFileName> parsedName = DidupFileName.parse(filename);
            if (parsedName.isEmpty()) {
                continue;
            }
            Path normalized = candidate.toAbsolutePath().normalize();
            seen.add(normalized);
            try {
                if (Files.isSymbolicLink(normalized)
                        || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                BasicFileAttributes attributes = Files.readAttributes(
                        normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                found++;
                Stability entry = stability.get(normalized);
                if (entry == null || !entry.matches(attributes)) {
                    entry = new Stability(
                            attributes.size(),
                            attributes.lastModifiedTime().toMillis(),
                            String.valueOf(attributes.fileKey()),
                            1);
                    stability.put(normalized, entry);
                    continue;
                }
                entry.scans++;
                if (entry.scans < properties.getImports().getStabilityScans()) {
                    continue;
                }

                ImportService.ImportOutcome outcome = importService.importFile(normalized, parsedName.get());
                if (outcome.status() == ImportService.ImportOutcome.Status.IMPORTED) {
                    imported++;
                    log.info("Importato {} ({} elementi)", filename, outcome.assignmentCount());
                } else if (outcome.status() == ImportService.ImportOutcome.Status.ERROR) {
                    errors++;
                    if (firstError == null) {
                        firstError = filename + ": " + outcome.message();
                    }
                    log.warn("Importazione di {} non riuscita: {}", filename, outcome.message());
                } else if (outcome.status() == ImportService.ImportOutcome.Status.CONFLICT) {
                    errors++;
                    if (firstError == null) {
                        firstError = filename + ": " + outcome.message();
                    }
                    log.warn("Conflitto su {}: {}", filename, outcome.message());
                }

                if (!Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    stability.remove(normalized);
                }
            } catch (Exception ex) {
                errors++;
                if (firstError == null) {
                    firstError = filename + ": " + ex.getMessage();
                }
                log.error("Errore non gestito sul file {}", filename, ex);
            }
        }

        stability.keySet().removeIf(path -> !seen.contains(path));
        state.setLastError(firstError);
        if (found > 0 || imported > 0 || errors > 0) {
            log.debug("Scansione completata: {} file DidUP, {} importati, {} errori", found, imported, errors);
        }
        publishStatusIfChanged(previousState, imported > 0);
    }

    private void publishStatusIfChanged(MonitorState.State previousState, boolean force) {
        if (!force && previousState == state.getState()) {
            return;
        }
        try {
            events.publish(MidaEvents.IMPORT_STATUS, statusService.status());
        } catch (RuntimeException ex) {
            log.debug("Impossibile pubblicare lo stato di importazione: {}", ex.getMessage());
        }
    }

    private static final class Stability {
        private final long size;
        private final long modified;
        private final String fileKey;
        private int scans;

        private Stability(long size, long modified, String fileKey, int scans) {
            this.size = size;
            this.modified = modified;
            this.fileKey = fileKey;
            this.scans = scans;
        }

        private boolean matches(BasicFileAttributes attributes) {
            return size == attributes.size()
                    && modified == attributes.lastModifiedTime().toMillis()
                    && fileKey.equals(String.valueOf(attributes.fileKey()));
        }
    }
}
