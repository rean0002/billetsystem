package dk.billetsystem.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Den generelle fejlhåndtering: uventede fejl giver 500 uden interne detaljer. */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void uventetFejlGiver500MedKortBesked() throws Exception {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new IllegalStateException("SQL: connection refused på db:5432"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(Map.of("fejl", "Der skete en uventet fejl"), response.getBody());
    }

    @Test
    void springsEgneFejlBeholderStatuskoden() throws Exception {
        ResponseEntity<Map<String, Object>> response =
                handler.handleUnexpected(new HttpRequestMethodNotSupportedException("PUT"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
    }

    @Test
    void manglendeAdgangOverladesTilSpringSecurity() {
        assertThrows(AccessDeniedException.class, () -> handler.handleUnexpected(new AccessDeniedException("nej")));
    }
}
