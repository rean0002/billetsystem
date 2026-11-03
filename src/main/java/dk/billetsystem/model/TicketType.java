package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * En billettype, fx "Early Bird" (195 kr.) eller "Standard" (295 kr.).
 * Svarer til tabellen "ticket_type".
 *
 * Prisen gemmes i øre (29500 = 295,00 kr.), så vi aldrig får afrundingsfejl.
 */
@Entity
@Table(name = "ticket_type")
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Mange billettyper hører til ét event (kolonnen event_id er fremmednøglen)
    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    private String name;
    private int priceOre;
    private OffsetDateTime validFrom;
    private OffsetDateTime validTo; // null = ingen slutdato
    private boolean active;

    protected TicketType() {
    }

    public TicketType(Event event, String name, int priceOre,
                      OffsetDateTime validFrom, OffsetDateTime validTo, boolean active) {
        this.event = event;
        update(name, priceOre, validFrom, validTo, active);
    }

    /**
     * Ændrer billettypen. Allerede solgte billetter påvirkes ikke, for hver deltager
     * har sin egen kopi af prisen (Participant.priceOre).
     */
    public void update(String name, int priceOre, OffsetDateTime validFrom, OffsetDateTime validTo, boolean active) {
        this.name = name;
        this.priceOre = priceOre;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
    }

    /** Kan billettypen købes på tidspunktet "now"? Samme regel som TicketTypeRepository.findCurrent. */
    public boolean isAvailableAt(OffsetDateTime now) {
        return active && !validFrom.isAfter(now) && (validTo == null || !validTo.isBefore(now));
    }

    public Integer getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public String getName() {
        return name;
    }

    public int getPriceOre() {
        return priceOre;
    }

    public OffsetDateTime getValidFrom() {
        return validFrom;
    }

    public OffsetDateTime getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }
}
