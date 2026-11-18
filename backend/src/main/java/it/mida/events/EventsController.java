package it.mida.events;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Eventi", description = "Server-Sent Events per aggiornamenti in tempo reale")
public class EventsController {

    private final MidaEventPublisher publisher;

    public EventsController(MidaEventPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Apre lo stream SSE degli aggiornamenti MIDA")
    public SseEmitter events() {
        return publisher.subscribe();
    }
}
