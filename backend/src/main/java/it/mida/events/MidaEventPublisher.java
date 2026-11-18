package it.mida.events;

import it.mida.config.MidaProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Lightweight in-process SSE hub. A notification failure never affects an
 * import: every emitter is isolated and errors only remove the broken client.
 */
@Service
public class MidaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(MidaEventPublisher.class);

    private final MidaProperties properties;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final AtomicLong eventIds = new AtomicLong();

    public MidaEventPublisher(MidaProperties properties) {
        this.properties = properties;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(properties.getEvents().getEmitterTimeoutMs());
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(eventIds.incrementAndGet()))
                    .name("connected")
                    .data(new MidaEventMessage("connected", Instant.now(), null), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitters.remove(emitter);
            emitter.completeWithError(ex);
        }
        return emitter;
    }

    public void publish(String type, Object data) {
        if (emitters.isEmpty()) {
            return;
        }
        String eventId = Long.toString(eventIds.incrementAndGet());
        MidaEventMessage message = new MidaEventMessage(type, Instant.now(), data);
        List<SseEmitter> broken = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name(type)
                        .data(message, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                broken.add(emitter);
            }
        }
        broken.forEach(emitter -> {
            emitters.remove(emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // The client is already gone.
            }
        });
        if (!broken.isEmpty()) {
            log.debug("Rimossi {} client SSE disconnessi", broken.size());
        }
    }

    @Scheduled(fixedDelayString = "${mida.events.heartbeat-ms:25000}", initialDelayString = "10000")
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        List<SseEmitter> broken = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception ex) {
                broken.add(emitter);
            }
        }
        broken.forEach(emitters::remove);
    }

    public int connectedClients() {
        return emitters.size();
    }

    public record MidaEventMessage(String type, Instant at, Object data) {
    }
}
