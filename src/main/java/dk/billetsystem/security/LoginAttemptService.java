package dk.billetsystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Beskytter login mod gætteri: efter for mange forkerte kodeord låses brugernavnet (eller IP-adressen) en stund.
 *
 * Reglerne:
 * - 5 forkerte forsøg på samme brugernavn inden for 15 minutter -> brugernavnet er låst i 15 minutter.
 * - 20 forkerte forsøg fra samme IP-adresse inden for 15 minutter -> IP-adressen er låst i 15 minutter.
 * - Et vellykket login nulstiller tællerne.
 *
 * Brugernavne, der ikke findes, tælles på samme måde, så man ikke kan se på låsen, om et brugernavn findes.
 * Tællerne ligger i hukommelsen og nulstilles, når programmet genstarter. Det er nok til ét program med få admins.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final int MAX_FAILURES_PER_USERNAME = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK_TIME = Duration.ofMinutes(15);

    /** Ryd gamle tællere op, når der er så mange, så hukommelsen ikke vokser uden grænse. */
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Failures> byUsername = new ConcurrentHashMap<>();
    private final Map<String, Failures> byIp = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    /** Er brugernavnet eller IP-adressen låst lige nu? */
    public boolean isBlocked(String username, String ip) {
        Instant now = clock.instant();
        return isLocked(byUsername.get(usernameKey(username)), now) || isLocked(byIp.get(ip), now);
    }

    public void loginFailed(String username, String ip) {
        Instant now = clock.instant();
        if (record(byUsername, usernameKey(username), MAX_FAILURES_PER_USERNAME, now)) {
            log.warn("Brugernavnet '{}' er låst i {} minutter efter {} forkerte login-forsøg",
                    username, LOCK_TIME.toMinutes(), MAX_FAILURES_PER_USERNAME);
        }
        if (ip != null && record(byIp, ip, MAX_FAILURES_PER_IP, now)) {
            log.warn("IP-adressen {} er låst i {} minutter efter {} forkerte login-forsøg",
                    ip, LOCK_TIME.toMinutes(), MAX_FAILURES_PER_IP);
        }
        cleanUpIfLarge(now);
    }

    public void loginSucceeded(String username, String ip) {
        byUsername.remove(usernameKey(username));
        if (ip != null) byIp.remove(ip);
    }

    // -----------------------------------------------------------------

    /** Tæller et forkert forsøg. Returnerer true, hvis det fik nøglen til at blive låst. */
    private static boolean record(Map<String, Failures> map, String key, int max, Instant now) {
        Failures f = map.compute(key, (k, old) -> {
            // Ny tæller, hvis der ikke er nogen, eller hvis den gamle periode er udløbet
            if (old == null || (old.lockedUntil == null && old.firstFailure.plus(WINDOW).isBefore(now))
                    || (old.lockedUntil != null && !old.lockedUntil.isAfter(now))) {
                return new Failures(1, now, null);
            }
            int count = old.count + 1;
            Instant lockedUntil = old.lockedUntil;
            if (lockedUntil == null && count >= max) lockedUntil = now.plus(LOCK_TIME);
            return new Failures(count, old.firstFailure, lockedUntil);
        });
        return f.count == max && f.lockedUntil != null;
    }

    private static boolean isLocked(Failures f, Instant now) {
        return f != null && f.lockedUntil != null && f.lockedUntil.isAfter(now);
    }

    private void cleanUpIfLarge(Instant now) {
        if (byUsername.size() + byIp.size() < CLEANUP_THRESHOLD) return;
        byUsername.values().removeIf(f -> isExpired(f, now));
        byIp.values().removeIf(f -> isExpired(f, now));
    }

    private static boolean isExpired(Failures f, Instant now) {
        return f.lockedUntil != null ? !f.lockedUntil.isAfter(now) : f.firstFailure.plus(WINDOW).isBefore(now);
    }

    /** "Admin " og "admin" er samme brugernavn. */
    private static String usernameKey(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Antal forkerte forsøg siden firstFailure, og evt. hvornår låsen udløber. */
    private record Failures(int count, Instant firstFailure, Instant lockedUntil) {
    }
}
