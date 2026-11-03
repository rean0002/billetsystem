package dk.billetsystem.service;

import dk.billetsystem.dto.AdminEventResponse;
import dk.billetsystem.dto.AdminOrderResponse;
import dk.billetsystem.dto.AdminTicketTypeResponse;
import dk.billetsystem.dto.OverviewResponse;
import dk.billetsystem.dto.OverviewResponse.DailySales;
import dk.billetsystem.dto.OverviewResponse.EventInfo;
import dk.billetsystem.dto.OverviewResponse.Money;
import dk.billetsystem.dto.OverviewResponse.Recent;
import dk.billetsystem.dto.OverviewResponse.TicketTypeSales;
import dk.billetsystem.dto.OverviewResponse.Tickets;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.Participant;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import dk.billetsystem.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Regner tallene ud til adminsidens overblik.
 *
 * Tallene gælder ét event ad gangen. Vælger admin ikke et, bruges det næste kommende (se EventService.defaultEvent).
 * "Solgt" deles op i betalt (PAID) og afventer (PENDING). Annullerede og refunderede tæller ikke som solgt.
 */
@Service
public class StatisticsService {

    /** Datoer og "i dag" regnes i dansk tid, ikke serverens tidszone. */
    public static final ZoneId DANISH_TIME = ZoneId.of("Europe/Copenhagen");
    private static final int DAYS_IN_CHART = 30;

    private final CustomerOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final EventService eventService;
    private final AdminOrderService adminOrderService;

    public StatisticsService(CustomerOrderRepository orderRepository,
                             TicketTypeRepository ticketTypeRepository,
                             EventService eventService,
                             AdminOrderService adminOrderService) {
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventService = eventService;
        this.adminOrderService = adminOrderService;
    }

    @Transactional(readOnly = true)
    public OverviewResponse overview(Integer eventId) {
        Event event = eventService.getOrDefault(eventId);
        List<CustomerOrder> orders = ordersFor(event);
        LocalDate today = LocalDate.now(DANISH_TIME);

        // Bestillinger pr. status, også dem med 0
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Arrays.stream(OrderStatus.values()).forEach(s -> byStatus.put(s.name(), 0L));
        orders.forEach(o -> byStatus.merge(o.getStatus().name(), 1L, Long::sum));

        long paidOre = sumTotal(orders, OrderStatus.PAID);
        long pendingOre = sumTotal(orders, OrderStatus.PENDING);
        long refundedOre = sumTotal(orders, OrderStatus.REFUNDED);
        long ticketsPaid = countTickets(orders, OrderStatus.PAID);
        long ticketsPending = countTickets(orders, OrderStatus.PENDING);

        List<CustomerOrder> active = orders.stream().filter(StatisticsService::isActive).toList();
        long activeOre = paidOre + pendingOre;
        Money money = new Money(paidOre, pendingOre, refundedOre, active.isEmpty() ? 0 : activeOre / active.size());
        Tickets tickets = new Tickets(ticketsPaid, ticketsPending,
                active.isEmpty() ? 0 : (double) (ticketsPaid + ticketsPending) / active.size());

        LocalDate weekAgo = today.minusDays(6);
        List<CustomerOrder> lastWeek = active.stream().filter(o -> !localDate(o).isBefore(weekAgo)).toList();
        Recent recent = new Recent(
                lastWeek.size(),
                lastWeek.stream().mapToLong(o -> o.getParticipants().size()).sum(),
                active.stream().filter(o -> localDate(o).equals(today)).count());

        List<TicketTypeSales> typeSales = ticketTypeSales(event, orders).stream()
                .map(t -> new TicketTypeSales(t.id(), t.name(), t.priceOre(), t.availableNow(), t.soldPaid(), t.soldPending(), t.revenuePaidOre()))
                .toList();

        EventInfo eventInfo = new EventInfo(event.getId(), event.getName(), event.getStartDate(), event.getEndDate(),
                ChronoUnit.DAYS.between(today, event.getStartDate()), event.getMaxParticipants());

        long overdue = orders.stream().map(adminOrderService::toResponse).filter(AdminOrderResponse::overdue).count();

        return new OverviewResponse(eventInfo, money, tickets, byStatus, overdue, recent, typeSales,
                dailySales(active, today), orders.stream().limit(5).map(adminOrderService::toResponse).toList());
    }

    /** Alle billettyper til et event med salgstal. Bruges også på siden "Billettyper". */
    @Transactional(readOnly = true)
    public List<AdminTicketTypeResponse> ticketTypesWithSales(Integer eventId) {
        Event event = eventService.getOrDefault(eventId);
        return ticketTypeSales(event, ordersFor(event));
    }

    /** Alle events med solgte billetter og omsætning. Til siden "Events" og vælgeren i menuen. */
    @Transactional(readOnly = true)
    public List<AdminEventResponse> eventsWithSales() {
        List<CustomerOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        Integer defaultId = eventService.findAll().isEmpty() ? null : eventService.defaultEvent().getId();
        return eventService.findAll().stream().map(event -> {
            List<CustomerOrder> forEvent = orders.stream().filter(o -> belongsTo(o, event)).toList();
            return AdminEventResponse.from(event,
                    countTickets(forEvent, OrderStatus.PAID),
                    countTickets(forEvent, OrderStatus.PENDING),
                    sumTotal(forEvent, OrderStatus.PAID),
                    event.getId().equals(defaultId));
        }).toList();
    }

    // -----------------------------------------------------------------

    private List<CustomerOrder> ordersFor(Event event) {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream().filter(o -> belongsTo(o, event)).toList();
    }

    static boolean belongsTo(CustomerOrder order, Event event) {
        return order.getEvent() != null && order.getEvent().getId().equals(event.getId());
    }

    private List<AdminTicketTypeResponse> ticketTypeSales(Event event, List<CustomerOrder> orders) {
        List<AdminTicketTypeResponse> result = new ArrayList<>();
        for (TicketType type : ticketTypeRepository.findByEventIdOrderByValidFrom(event.getId())) {
            int paid = 0, pending = 0, revenue = 0;
            for (CustomerOrder o : orders) {
                for (Participant p : o.getParticipants()) {
                    if (!p.getTicketType().getId().equals(type.getId())) continue;
                    if (o.getStatus() == OrderStatus.PAID) {
                        paid++;
                        revenue += p.getPriceOre();
                    } else if (o.getStatus() == OrderStatus.PENDING) {
                        pending++;
                    }
                }
            }
            result.add(AdminTicketTypeResponse.from(type, paid, pending, revenue));
        }
        return result;
    }

    /** Billetter pr. dag de seneste 30 dage. Dage uden salg kommer med som 0, så grafen ikke springer dage over. */
    private List<DailySales> dailySales(List<CustomerOrder> active, LocalDate today) {
        Map<LocalDate, long[]> perDay = new LinkedHashMap<>();
        for (int i = DAYS_IN_CHART - 1; i >= 0; i--) {
            perDay.put(today.minusDays(i), new long[2]);
        }
        for (CustomerOrder o : active) {
            long[] day = perDay.get(localDate(o));
            if (day == null) continue; // ældre end 30 dage
            day[o.getStatus() == OrderStatus.PAID ? 0 : 1] += o.getParticipants().size();
        }
        return perDay.entrySet().stream()
                .map(e -> new DailySales(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private static boolean isActive(CustomerOrder o) {
        return o.getStatus() == OrderStatus.PAID || o.getStatus() == OrderStatus.PENDING;
    }

    private static LocalDate localDate(CustomerOrder o) {
        OffsetDateTime created = o.getCreatedAt();
        return created.atZoneSameInstant(DANISH_TIME).toLocalDate();
    }

    private static long sumTotal(List<CustomerOrder> orders, OrderStatus status) {
        return orders.stream().filter(o -> o.getStatus() == status).mapToLong(CustomerOrder::getTotalOre).sum();
    }

    private static long countTickets(List<CustomerOrder> orders, OrderStatus status) {
        return orders.stream().filter(o -> o.getStatus() == status).mapToLong(o -> o.getParticipants().size()).sum();
    }
}
