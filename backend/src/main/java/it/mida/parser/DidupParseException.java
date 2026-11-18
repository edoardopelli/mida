package it.mida.parser;

public class DidupParseException extends RuntimeException {

    public DidupParseException(String message) {
        super(message);
    }

    public DidupParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
