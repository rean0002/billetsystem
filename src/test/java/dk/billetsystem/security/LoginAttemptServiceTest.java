package dk.billetsystem.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester reglerne for låsning af login. Testene bruger et ur, man selv kan stille frem,
 * så man ikke skal vente 15 minutter.
 */
class LoginAttemptServiceTest {

    private static final String IP = "203.0.113.7";
    private static final String OTHER_IP = "203.0.113.8";

    private TestClock clock;
    private LoginAttemptService attempts;

    @BeforeEach
    void setUp() {
        clock = new TestClock(Instant.parse("2026-11-03T12:00:00Z"));
        attempts = new LoginAttemptService(clock);
    }

    @Test
    void fireForkerteForsøgLåserIkke() {
        failTimes("admin", IP, 4);
        assertFalse(attempts.isBlocked("admin", IP));
    }

    @Test
    void femForkerteForsøgLåserBrugernavnet() {
        failTimes("admin", IP, 5);
        assertTrue(attempts.isBlocked("admin", IP));
        // Også fra en anden computer, og uanset store/små bogstaver og mellemrum
        assertTrue(attempts.isBlocked(" Admin ", OTHER_IP));
    }

    @Test
    void låsenUdløberEfter15Minutter() {
        failTimes("admin", IP, 5);
        clock.advance(Duration.ofMinutes(14));
        assertTrue(attempts.isBlocked("admin", IP));
        clock.advance(Duration.ofMinutes(1));
        assertFalse(attempts.isBlocked("admin", IP));
    }

    @Test
    void forsøgUdenForPeriodenTællerIkkeMed() {
        failTimes("admin", IP, 4);
        clock.advance(Duration.ofMinutes(16));
        failTimes("admin", IP, 1);
        assertFalse(attempts.isBlocked("admin", IP));
    }

    @Test
    void vellykketLoginNulstillerTælleren() {
        failTimes("admin", IP, 4);
        attempts.loginSucceeded("admin", IP);
        failTimes("admin", IP, 4);
        assertFalse(attempts.isBlocked("admin", IP));
    }

    @Test
    void mangeBrugernavneFraSammeIpLåserIpAdressen() {
        for (int i = 0; i < 20; i++) {
            attempts.loginFailed("bruger" + i, IP);
        }
        assertTrue(attempts.isBlocked("et-helt-nyt-navn", IP));
        assertFalse(attempts.isBlocked("et-helt-nyt-navn", OTHER_IP));
    }

    @Test
    void ukendteBrugernavneLåsesPåSammeMåde() {
        failTimes("findes-ikke", IP, 5);
        assertTrue(attempts.isBlocked("findes-ikke", IP));
        assertFalse(attempts.isBlocked("admin", OTHER_IP));
    }

    private void failTimes(String username, String ip, int times) {
        for (int i = 0; i < times; i++) {
            attempts.loginFailed(username, ip);
        }
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
