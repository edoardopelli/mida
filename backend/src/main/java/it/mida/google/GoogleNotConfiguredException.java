package it.mida.google;

public class GoogleNotConfiguredException extends RuntimeException {

    public GoogleNotConfiguredException() {
        super("Integrazione Google non configurata: importa il JSON del client OAuth da Impostazioni → Account Google");
    }
}
