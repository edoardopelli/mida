package it.mida.notifications.api;

public record VapidPublicKeyDto(String publicKey, boolean enabled, String subject) {
}
