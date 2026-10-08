package dk.billetsystem.dto;

import dk.billetsystem.model.TicketType;

import java.time.OffsetDateTime;

/**
 * Det, hjemmesiden får at vide om en billettype. Vi sender med vilje ikke selve
 * databaseobjektet ud, kun de felter, som besøgende har brug for (det kaldes en DTO).
 */
public record TicketTypeResponse(
        Integer id,
        String eventName,
        String name,
        int priceOre,
        OffsetDateTime validFrom,
        OffsetDateTime validTo
) {
    public static TicketTypeResponse from(TicketType t) {
        return new TicketTypeResponse(
                t.getId(),
                t.getEvent().getName(),
                t.getName(),
                t.getPriceOre(),
                t.getValidFrom(),
                t.getValidTo());
    }
}
