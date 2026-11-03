package dk.billetsystem.dto;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.Participant;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * En bestilling, som adminsiden ser den: med kontaktoplysninger, deltagere og
 * hvilke statusskift admin må lave lige nu (så knapperne kun vises, når de giver mening).
 */
public record AdminOrderResponse(
        Integer id,
        String contactName,
        String email,
        String phone,
        String note,
        List<String> seatWith,
        String status,
        int totalOre,
        int ticketCount,
        OffsetDateTime createdAt,
        OffsetDateTime paidAt,
        OffsetDateTime confirmationSentAt,
        OffsetDateTime paidMailSentAt,
        LocalDate paymentDueDate,
        boolean overdue,
        List<ParticipantLine> participants,
        List<String> allowedStatuses
) {
    public record ParticipantLine(Integer id, String fullName, String ticketType, int priceOre,
                                  boolean beginnerTable, boolean needsTableEnd) {
        static ParticipantLine from(Participant p) {
            return new ParticipantLine(p.getId(), p.getFullName(), p.getTicketType().getName(), p.getPriceOre(),
                    p.isBeginnerTable(), p.isNeedsTableEnd());
        }
    }

    /**
     * @param dueDate sidste betalingsdag (se PaymentSettingsService.dueDate). Kun afventende bestillinger
     *                kan være "overdue", altså have overskredet fristen.
     */
    public static AdminOrderResponse from(CustomerOrder order, LocalDate dueDate, LocalDate today) {
        List<String> allowed = Arrays.stream(OrderStatus.values())
                .filter(order.getStatus()::canChangeTo)
                .map(Enum::name)
                .toList();
        return new AdminOrderResponse(
                order.getId(),
                order.getContactName(),
                order.getEmail(),
                order.getPhone(),
                order.getNote(),
                order.getSeatWithNames(),
                order.getStatus().name(),
                order.getTotalOre(),
                order.getParticipants().size(),
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getConfirmationSentAt(),
                order.getPaidMailSentAt(),
                dueDate,
                order.getStatus() == OrderStatus.PENDING && today.isAfter(dueDate),
                order.getParticipants().stream().map(ParticipantLine::from).toList(),
                allowed);
    }
}
