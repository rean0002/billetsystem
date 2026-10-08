package dk.billetsystem.dto;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Participant;

import java.time.OffsetDateTime;
import java.util.List;

/** Svaret, når en bestilling er oprettet. */
public record OrderResponse(
        Integer id,
        String status,
        int totalOre,
        List<String> participantNames,
        OffsetDateTime createdAt
) {
    public static OrderResponse from(CustomerOrder order) {
        List<String> names = order.getParticipants().stream()
                .map(Participant::getFullName)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalOre(),
                names,
                order.getCreatedAt());
    }
}
