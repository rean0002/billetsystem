package dk.billetsystem.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Samler fejlbeskeder ét sted, så hjemmesiden altid får et læsbart svar, fx
 * {"fejl": "Ugyldige data", "felter": {"email": "E-mail mangler"}}.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Vores egne fejl, fx "billettypen kan ikke købes lige nu"
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.<String, Object>of("fejl", String.valueOf(ex.getReason())));
    }

    // Fejl fra valideringen i OrderRequest (@NotBlank, @Email ...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> felter = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> felter.put(e.getField(), String.valueOf(e.getDefaultMessage())));
        return ResponseEntity.badRequest()
                .body(Map.<String, Object>of("fejl", "Ugyldige data", "felter", felter));
    }

    /**
     * Alt andet. Uventede fejl logges med hele forklaringen, men brugeren får kun en kort besked,
     * så interne detaljer (fx SQL eller filstier) aldrig sendes ud.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) throws Exception {
        // Manglende login eller adgang klares af Spring Security (401/403), ikke her
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        // Springs egne "forventede" fejl beholder deres statuskode, fx 404 eller forkert HTTP-metode
        if (ex instanceof ErrorResponse error) {
            return ResponseEntity.status(error.getStatusCode())
                    .body(Map.<String, Object>of("fejl", "Forespørgslen kunne ikke behandles"));
        }
        // Ulæselig JSON eller forkert type i adressen, fx ?eventId=abc
        if (ex instanceof HttpMessageNotReadableException || ex instanceof TypeMismatchException) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("fejl", "Ugyldige data"));
        }
        log.error("Uventet fejl", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.<String, Object>of("fejl", "Der skete en uventet fejl"));
    }
}
