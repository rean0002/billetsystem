package dk.billetsystem.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Højst 10 bestillinger i timen pr. IP-adresse. */
class OrderRateLimiterTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-11-04T10:00:00Z"));
    private final OrderRateLimiter limiter = new OrderRateLimiter(clock);

    @Test
    void tiBestillingerErOk() {
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> limiter.check("203.0.113.7"));
        }
    }

    @Test
    void denEllevteAfvisesMed429() {
        for (int i = 0; i < 10; i++) limiter.check("203.0.113.7");

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> limiter.check("203.0.113.7"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
    }

    @Test
    void andreIpAdresserPåvirkesIkke() {
        for (int i = 0; i < 10; i++) limiter.check("203.0.113.7");
        assertDoesNotThrow(() -> limiter.check("203.0.113.8"));
    }

    @Test
    void grænsenNulstillesEfterEnTime() {
        for (int i = 0; i < 10; i++) limiter.check("203.0.113.7");
        clock.advance(Duration.ofMinutes(59));
        assertThrows(ResponseStatusException.class, () -> limiter.check("203.0.113.7"));
        clock.advance(Duration.ofMinutes(1));
        assertDoesNotThrow(() -> limiter.check("203.0.113.7"));
    }

    /** Et ur, testen selv kan stille frem. */
    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
