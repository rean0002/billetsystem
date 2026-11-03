package dk.billetsystem.service;

import dk.billetsystem.dto.OrderRequest;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.Participant;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import dk.billetsystem.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final TicketService ticketService;
    private final EventService eventService;
    private final EventRepository eventRepository;

    public OrderService(CustomerOrderRepository orderRepository, TicketService ticketService,
                        EventService eventService, EventRepository eventRepository) {
        this.orderRepository = orderRepository;
        this.ticketService = ticketService;
        this.eventService = eventService;
        this.eventRepository = eventRepository;
    }

    /**
     * Opretter en bestilling med status PENDING (ikke betalt endnu).
     *
     * @Transactional betyder "alt eller intet": går noget galt halvvejs, bliver intet gemt i databasen.
     */
    @Transactional
    public CustomerOrder createOrder(OrderRequest request) {
        // De billettyper, der kan købes lige nu, med id som nøgle
        Map<Integer, TicketType> available = ticketService.findCurrentTicketTypes().stream()
                .collect(Collectors.toMap(TicketType::getId, Function.identity()));

        CustomerOrder order = new CustomerOrder(
                request.contactName().trim(),
                request.email().trim(),
                request.phone(),
                request.note(),
                normalizeNames(request.seatWith()));

        int total = 0;
        for (OrderRequest.ParticipantRequest p : request.participants()) {
            TicketType type = available.get(p.ticketTypeId());
            if (type == null) {
                // Typen findes ikke, er slået fra, eller perioden er udløbet (fx early bird)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Billettypen " + p.ticketTypeId() + " findes ikke eller kan ikke købes lige nu");
            }
            // Prisen tages fra databasen, aldrig fra det, hjemmesiden sender
            order.addParticipant(new Participant(type, p.fullName().trim(), type.getPriceOre(),
                    Boolean.TRUE.equals(p.beginnerTable()), Boolean.TRUE.equals(p.needsTableEnd())));
            total += type.getPriceOre();
        }
        order.setTotalOre(total);

        checkOneEventAndCapacity(order);
        return orderRepository.save(order);
    }

    /**
     * En bestilling må kun indeholde billetter til ét event, og eventet må ikke blive overbooket.
     */
    private void checkOneEventAndCapacity(CustomerOrder order) {
        long events = order.getParticipants().stream()
                .map(p -> p.getTicketType().getEvent().getId())
                .distinct()
                .count();
        if (events > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "En bestilling kan kun indeholde billetter til ét event");
        }

        // Lås eventet, så to samtidige bestillinger ikke kan tage de samme sidste pladser
        Event event = eventRepository.findByIdForUpdate(order.getEvent().getId()).orElseThrow();
        Long seatsLeft = eventService.seatsLeft(event);
        int wanted = order.getParticipants().size();
        if (seatsLeft != null && wanted > seatsLeft) {
            String message = seatsLeft == 0
                    ? event.getName() + " er desværre udsolgt"
                    : "Der er kun " + seatsLeft + " " + (seatsLeft == 1 ? "plads" : "pladser") + " tilbage til " + event.getName();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    /**
     * "Anna Andersen, Bente Berg" eller ét navn pr. linje -> ét navn pr. linje, uden tomme linjer og
     * uden dubletter ("anna andersen" og "Anna Andersen" regnes som samme navn, det første beholdes).
     * Returnerer null, hvis der ingen navne er.
     */
    static String normalizeNames(String names) {
        if (names == null) return null;
        Map<String, String> unique = new LinkedHashMap<>();
        Arrays.stream(names.split("[\\n,;]+"))
                .map(n -> n.trim().replaceAll("\\s+", " "))
                .filter(n -> !n.isEmpty())
                .forEach(n -> unique.putIfAbsent(n.toLowerCase(Locale.ROOT), n));
        return unique.isEmpty() ? null : String.join("\n", unique.values());
    }
}
