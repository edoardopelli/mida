package it.mida.google;

/**
 * Il token Google non è più valido o i permessi sono stati revocati: serve una
 * nuova autorizzazione da parte dell'utente.
 */
public class GoogleReauthRequiredException extends GoogleApiException {

    public GoogleReauthRequiredException(String message) {
        super(401, message);
    }
}
