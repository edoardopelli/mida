package it.mida.common;

import it.mida.parser.DidupParseException;
import it.mida.google.GoogleApiException;
import it.mida.google.GoogleNotConfiguredException;
import it.mida.google.GoogleNotConnectedException;
import it.mida.google.GoogleReauthRequiredException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MidaNotFoundException.class)
    public ProblemDetail notFound(MidaNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Risorsa non trovata", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail resourceNotFound(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Risorsa non trovata", "La risorsa richiesta non esiste.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail methodArgumentNotValid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Richiesta non valida", detail);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handlerMethodValidation(HandlerMethodValidationException ex) {
        String detail = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Parametri non validi", detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Parametri non validi", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ProblemDetail badRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Richiesta non valida", ex.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "Metodo non consentito",
                "Il metodo " + ex.getMethod() + " non è supportato per questa risorsa.");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientDisconnected(AsyncRequestNotUsableException ex) {
        log.debug("Connessione SSE interrotta dal client: {}", ex.getMessage());
    }

    @ExceptionHandler(DidupParseException.class)
    public ProblemDetail parseError(DidupParseException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Errore di parsing", ex.getMessage());
    }

    @ExceptionHandler(GoogleNotConfiguredException.class)
    public ProblemDetail googleNotConfigured(GoogleNotConfiguredException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Google non configurato", ex.getMessage());
    }

    @ExceptionHandler(GoogleNotConnectedException.class)
    public ProblemDetail googleNotConnected(GoogleNotConnectedException ex) {
        return problem(HttpStatus.CONFLICT, "Google non collegato", ex.getMessage());
    }

    @ExceptionHandler(GoogleReauthRequiredException.class)
    public ProblemDetail googleReauth(GoogleReauthRequiredException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Riconnessione Google necessaria", ex.getMessage());
    }

    @ExceptionHandler(GoogleApiException.class)
    public ProblemDetail googleApi(GoogleApiException ex) {
        return problem(HttpStatus.BAD_GATEWAY, "Errore Google", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex) {
        log.error("Errore non gestito", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno",
                "Si è verificato un errore interno. Consultare i log del backend.");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null ? title : detail);
        problem.setTitle(title);
        return problem;
    }
}
