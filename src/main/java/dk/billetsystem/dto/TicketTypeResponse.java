package dk.billetsystem.dto;

import dk.billetsystem.model.Event;
import dk.billetsystem.model.TicketType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Det, hjemmesiden får at vide om en billettype. Vi sender med vilje ikke selve
 * databaseobjektet ud, kun de felter, som besøgende har brug for (det kaldes en DTO).
 *
 * Event-felterne gør, at bestillingssiden kan gruppere billetterne, når flere events er til salg.
 * eventSeatsLeft er null, hvis eventet ikke har en grænse for antal deltagere.
 */
public record TicketTypeResponse(
        Integer id,
        String name,
        int priceOre,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        Integer eventId,
        String eventName,
        LocalDate eventStartDate,
        LocalDate eventEndDate,
        String eventPublicInfo,
        Long eventSeatsLeft
) {
    public static TicketTypeResponse from(TicketType t, Long seatsLeft) {
        Event e = t.getEvent();
        return new TicketTypeResponse(
                t.getId(),
                t.getName(),
                t.getPriceOre(),
                t.getValidFrom(),
                t.getValidTo(),
                e.getId(),
                e.getName(),
                e.getStartDate(),
                e.getEndDate(),
                e.getPublicInfo(),
                seatsLeft);
    }
}
