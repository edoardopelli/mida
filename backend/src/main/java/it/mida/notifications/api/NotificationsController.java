package it.mida.notifications.api;

import it.mida.notifications.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifiche", description = "Web Push, preferenze e promemoria")
public class NotificationsController {

    private final NotificationService notificationService;

    public NotificationsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/public-key")
    @Operation(summary = "Restituisce la chiave pubblica VAPID per PushManager.subscribe")
    public VapidPublicKeyDto publicKey() {
        return notificationService.publicKeyInfo();
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Registra o aggiorna una sottoscrizione Web Push")
    public PushSubscriptionDto subscribe(
            @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        var subscription = notificationService.subscribe(request, userAgent);
        return new PushSubscriptionDto(subscription.getId(), subscription.getEndpoint(), subscription.isActive());
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disattiva una sottoscrizione Web Push")
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        notificationService.unsubscribe(request.endpoint());
    }

    @GetMapping("/preferences")
    @Operation(summary = "Legge le preferenze di notifica")
    public NotificationPreferencesDto preferences() {
        return notificationService.preferences();
    }

    @PutMapping("/preferences")
    @Operation(summary = "Aggiorna le preferenze di notifica")
    public NotificationPreferencesDto updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return notificationService.updatePreferences(request);
    }

    @PostMapping("/test")
    @Operation(summary = "Invia una notifica push di prova a tutte le sottoscrizioni attive")
    public TestNotificationResponse test() {
        return notificationService.sendTest();
    }

    public record PushSubscriptionDto(String id, String endpoint, boolean active) {
    }
}
