package dk.billetsystem.dto;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Participant;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Svaret, når en bestilling er oprettet. payment er null, hvis arrangørerne ikke har
 * udfyldt betalingsoplysninger endnu.
 */
public record OrderResponse(
        Integer id,
        String status,
        int totalOre,
        List<String> participantNames,
        OffsetDateTime createdAt,
        PaymentInstructions payment,
        String confirmationEmail
) {
    /** @param mailEnabled sendes der en bekræftelsesmail? I så fald står adressen i svaret, så siden kan nævne den. */
    public static OrderResponse from(CustomerOrder order, PaymentInstructions payment, boolean mailEnabled) {
        List<String> names = order.getParticipants().stream()
                .map(Participant::getFullName)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalOre(),
                names,
                order.getCreatedAt(),
                payment,
                mailEnabled ? order.getEmail() : null);
    }
}
