package it.mida.google;

public class GoogleApiException extends RuntimeException {

    private final int status;

    public GoogleApiException(String message) {
        this(0, message, null);
    }

    public GoogleApiException(String message, Throwable cause) {
        this(0, message, cause);
    }

    public GoogleApiException(int status, String message) {
        this(status, message, null);
    }

    public GoogleApiException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
