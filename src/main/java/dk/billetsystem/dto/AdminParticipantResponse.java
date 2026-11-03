package dk.billetsystem.dto;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Participant;

import java.time.OffsetDateTime;

/** Én linje på deltagerlisten: deltageren plus det vigtigste fra bestillingen. */
public record AdminParticipantResponse(
        Integer id,
        String fullName,
        Integer ticketTypeId,
        String ticketType,
        int priceOre,
        Integer orderId,
        String orderStatus,
        String contactName,
        String email,
        String phone,
        OffsetDateTime orderedAt
) {
    public static AdminParticipantResponse from(Participant p) {
        CustomerOrder o = p.getCustomerOrder();
        return new AdminParticipantResponse(
                p.getId(),
                p.getFullName(),
                p.getTicketType().getId(),
                p.getTicketType().getName(),
                p.getPriceOre(),
                o.getId(),
                o.getStatus().name(),
                o.getContactName(),
                o.getEmail(),
                o.getPhone(),
                o.getCreatedAt());
    }
}
