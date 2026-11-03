package dk.billetsystem.dto;

import dk.billetsystem.model.TicketType;

import java.time.OffsetDateTime;

/** En billettype på adminsiden, med salgstal. */
public record AdminTicketTypeResponse(
        Integer id,
        String name,
        int priceOre,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        boolean active,
        boolean availableNow,
        int soldPaid,
        int soldPending,
        int revenuePaidOre
) {
    public static AdminTicketTypeResponse from(TicketType t, int soldPaid, int soldPending, int revenuePaidOre) {
        return new AdminTicketTypeResponse(
                t.getId(), t.getName(), t.getPriceOre(), t.getValidFrom(), t.getValidTo(), t.isActive(),
                t.isAvailableAt(OffsetDateTime.now()), soldPaid, soldPending, revenuePaidOre);
    }
}
