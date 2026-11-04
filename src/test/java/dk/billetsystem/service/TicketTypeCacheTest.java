package dk.billetsystem.service;

import dk.billetsystem.dto.TicketTypeResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Cachen af billettyper: genbrug i 30 sekunder, og tøm først, når en ændring er gemt. */
class TicketTypeCacheTest {

    private final TestClock clock = new TestClock(Instant.parse("2026-11-04T10:00:00Z"));
    private final TicketTypeCache cache = new TicketTypeCache(clock);

    /** En "database", der tæller, hvor mange gange den bliver spurgt. */
    private final AtomicInteger loads = new AtomicInteger();
    private final Supplier<List<TicketTypeResponse>> loader = () -> {
        loads.incrementAndGet();
        return List.of(new TicketTypeResponse(2, "Standard", 29500, OffsetDateTime.parse("2026-10-01T00:00:00+02:00"),
                null, 1, "Hobbymesse 2027", LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 4), null, null));
    };

    @AfterEach
    void cleanUp() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void svaretGenbrugesI30Sekunder() {
        cache.get(loader);
        clock.advance(Duration.ofSeconds(29));
        cache.get(loader);
        assertEquals(1, loads.get());

        clock.advance(Duration.ofSeconds(1));
        cache.get(loader);
        assertEquals(2, loads.get());
    }

    @Test
    void evictUdenforTransaktionTømmerMedDetSamme() {
        cache.get(loader);
        cache.evict();
        cache.get(loader);
        assertEquals(2, loads.get());
    }

    @Test
    void evictITransaktionVenterTilCommit() {
        cache.get(loader);
        TransactionSynchronizationManager.initSynchronization();

        cache.evict();
        cache.get(loader); // ændringen er ikke gemt endnu, så det gamle svar bruges stadig
        assertEquals(1, loads.get());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        cache.get(loader);
        assertEquals(2, loads.get());
    }

    @Test
    void rullesTransaktionenTilbageBeholdesCachen() {
        cache.get(loader);
        TransactionSynchronizationManager.initSynchronization();

        cache.evict();
        // Ved rollback kaldes afterCompletion, men ikke afterCommit
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        cache.get(loader);
        assertEquals(1, loads.get());
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
