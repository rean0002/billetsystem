package dk.billetsystem.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Alle tal til forsiden af adminsiden, samlet i ét svar.
 * "Afventer" betyder bestilt, men endnu ikke betalt (status PENDING).
 * overdueOrders er de afventende bestillinger, hvor betalingsfristen er overskredet.
 */
public record OverviewResponse(
        EventInfo event,
        Money money,
        Tickets tickets,
        Map<String, Long> ordersByStatus,
        long overdueOrders,
        Recent recent,
        List<TicketTypeSales> ticketTypes,
        List<DailySales> daily,
        List<AdminOrderResponse> latestOrders
) {
    /** Messen. maxParticipants er null, hvis der ikke er sat en grænse. */
    public record EventInfo(Integer id, String name, LocalDate startDate, LocalDate endDate, long daysUntil, Integer maxParticipants) {
    }

    public record Money(long paidOre, long pendingOre, long refundedOre, long averageOrderOre) {
    }

    public record Tickets(long paid, long pending, double averagePerOrder) {
    }

    /** Aktivitet de seneste 7 dage (annullerede tæller ikke med). */
    public record Recent(long ordersLast7Days, long ticketsLast7Days, long ordersToday) {
    }

    public record TicketTypeSales(Integer id, String name, int priceOre, boolean availableNow, long paid, long pending, long revenuePaidOre) {
    }

    /** Billetter bestilt pr. dag (betalte + afventende). */
    public record DailySales(LocalDate date, long paid, long pending) {
    }
}
