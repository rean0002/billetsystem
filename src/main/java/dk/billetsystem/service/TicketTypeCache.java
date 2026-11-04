package dk.billetsystem.service;

import dk.billetsystem.dto.TicketTypeResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Husker svaret på GET /api/ticket-types/current (billettyper og ledige pladser) i op til 30 sekunder,
 * så databasen ikke skal spørges ved hvert besøg på bestillingssiden.
 *
 * Når noget ændrer svaret (billettyper, events, bestillinger), kalder servicelaget evict(), og cachen tømmes.
 * De 30 sekunder er et sikkerhedsnet, fx når en early bird-periode udløber på et bestemt klokkeslæt.
 *
 * Cachen kan ikke føre til overbooking: OrderService tjekker altid kapaciteten i databasen ved bestilling.
 */
@Component
public class TicketTypeCache {

    static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);

    private final AtomicReference<Entry> entry = new AtomicReference<>();
    private final Clock clock;

    public TicketTypeCache(Clock clock) {
        this.clock = clock;
    }

    /** Det gemte svar, eller et nyt fra loader, hvis der intet er, eller det er for gammelt. */
    public List<TicketTypeResponse> get(Supplier<List<TicketTypeResponse>> loader) {
        Instant now = clock.instant();
        Entry current = entry.get();
        if (current != null && current.expiresAt.isAfter(now)) {
            return current.value;
        }
        List<TicketTypeResponse> fresh = List.copyOf(loader.get());
        entry.set(new Entry(fresh, now.plus(TIME_TO_LIVE)));
        return fresh;
    }

    /**
     * Tømmer cachen. Kaldes inde i en transaktion, sker det først, når ændringen er gemt (efter commit).
     * Ellers kunne et andet besøg nå at fylde cachen med de gamle data, før ændringen var gemt.
     */
    public void evict() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entry.set(null);
                }
            });
        } else {
            entry.set(null);
        }
    }

    private record Entry(List<TicketTypeResponse> value, Instant expiresAt) {
    }
}
