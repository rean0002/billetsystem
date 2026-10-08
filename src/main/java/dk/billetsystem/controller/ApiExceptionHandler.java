package dk.billetsystem.controller;

import org.springframework.http.ResponseEntity;
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
}
