package it.mida.notifications.api;

public record TestNotificationResponse(int sent, int failed, int expired, int subscriptions) {
}
