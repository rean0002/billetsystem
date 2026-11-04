package dk.billetsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Begrænser, hvor mange bestillinger der kan oprettes fra samme IP-adresse: højst 10 i timen.
 * Det stopper fx et script, der fylder eventet med falske bestillinger, mens almindelige kunder
 * (og en familie på samme internetforbindelse) aldrig mærker det.
 *
 * Tællerne ligger i hukommelsen og nulstilles, når programmet genstarter.
 */
@Service
public class OrderRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(OrderRateLimiter.class);

    static final int MAX_ORDERS_PER_WINDOW = 10;
    static final Duration WINDOW = Duration.ofHours(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> byIp = new ConcurrentHashMap<>();
    private final Clock clock;

    public OrderRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Tæller et forsøg fra ip. Er grænsen nået, gives 429 med en forståelig besked. */
    public void check(String ip) {
        Instant now = clock.instant();
        Window window = byIp.compute(ip, (k, old) -> {
            // Ny periode, hvis der ikke er nogen, eller den gamle er udløbet
            if (old == null || !old.start.plus(WINDOW).isAfter(now)) return new Window(now, 1);
            return new Window(old.start, old.count + 1);
        });
        if (window.count > MAX_ORDERS_PER_WINDOW) {
            if (window.count == MAX_ORDERS_PER_WINDOW + 1) {
                log.warn("IP-adressen {} har nået grænsen på {} bestillinger i timen", ip, MAX_ORDERS_PER_WINDOW);
            }
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Der er oprettet for mange bestillinger fra din internetforbindelse. Prøv igen om en time.");
        }
        if (byIp.size() > CLEANUP_THRESHOLD) {
            byIp.values().removeIf(w -> !w.start.plus(WINDOW).isAfter(now));
        }
    }

    /** Antal forsøg siden start. */
    private record Window(Instant start, int count) {
    }
}
