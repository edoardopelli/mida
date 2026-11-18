package it.mida.google;

public class GoogleNotConnectedException extends RuntimeException {

    public GoogleNotConnectedException() {
        super("Nessun account Google collegato");
    }
}
