package dk.billetsystem.service;

import dk.billetsystem.dto.EventRequest;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.repository.CustomerOrderRepository;
import dk.billetsystem.repository.EventRepository;
import dk.billetsystem.repository.ParticipantRepository;
import dk.billetsystem.repository.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * Events, fx "Forårsmessen 2027" og "Efterårsmessen 2027".
 * Hver billettype hører til ét event, og en bestilling indeholder billetter til ét event.
 */
@Service
public class EventService {

    /** Bestillinger med disse statusser optager en plads. Annullerede og refunderede gør ikke. */
    public static final EnumSet<OrderStatus> TAKES_A_SEAT = EnumSet.of(OrderStatus.PAID, OrderStatus.PENDING);

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;
    private final CustomerOrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeCache ticketTypeCache;

    public EventService(EventRepository eventRepository, ParticipantRepository participantRepository,
                        CustomerOrderRepository orderRepository, TicketTypeRepository ticketTypeRepository,
                        TicketTypeCache ticketTypeCache) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
        this.orderRepository = orderRepository;
        this.ticketTypeRepository = ticketTypeRepository;
        this.ticketTypeCache = ticketTypeCache;
    }

    public List<Event> findAll() {
        return eventRepository.findAllByOrderByStartDateDesc();
    }

    public Event get(int id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + id + " findes ikke"));
    }

    /**
     * Det event, adminsiden viser, når man ikke har valgt et: det næste, der ikke er slut endnu.
     * Er alle events overstået, vises det seneste.
     */
    public Event defaultEvent() {
        LocalDate today = LocalDate.now(StatisticsService.DANISH_TIME);
        return eventRepository.findFirstByEndDateGreaterThanEqualOrderByStartDate(today)
                .or(eventRepository::findFirstByOrderByStartDateDesc)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der er ikke oprettet noget event endnu"));
    }

    /** Det valgte event, eller standard-eventet hvis eventId er null. */
    public Event getOrDefault(Integer eventId) {
        return eventId == null ? defaultEvent() : get(eventId);
    }

    /** Antal billetter, der optager en plads (betalte + afventende). */
    public long seatsTaken(Event event) {
        return participantRepository.countByEventAndStatus(event.getId(), TAKES_A_SEAT);
    }

    /**
     * Sikrer, at der er plads til wanted deltagere mere, ellers 400 med en forståelig besked.
     *
     * Eventets række låses, til transaktionen er færdig (SELECT ... FOR UPDATE). Bestiller eller genåbner
     * to personer de sidste pladser samtidig, må den ene vente, så eventet ikke kan blive overbooket.
     * Skal derfor kaldes inde i en @Transactional-metode, før bestillingen begynder at optage pladser.
     */
    public void reserveSeats(int eventId, int wanted) {
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + eventId + " findes ikke"));
        Long seatsLeft = seatsLeft(event);
        if (seatsLeft != null && wanted > seatsLeft) {
            String message = seatsLeft == 0
                    ? event.getName() + " er desværre udsolgt"
                    : "Der er kun " + seatsLeft + " " + (seatsLeft == 1 ? "plads" : "pladser") + " tilbage til " + event.getName();
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    /** Ledige pladser, eller null hvis eventet ikke har en grænse. */
    public Long seatsLeft(Event event) {
        if (event.getMaxParticipants() == null) return null;
        return Math.max(0, event.getMaxParticipants() - seatsTaken(event));
    }

    @Transactional
    public Event create(EventRequest request) {
        validate(request);
        ticketTypeCache.evict();
        return eventRepository.save(new Event(request.name().trim(), request.startDate(), request.endDate(),
                request.maxParticipants(), blankToNull(request.publicInfo())));
    }

    @Transactional
    public Event update(int id, EventRequest request) {
        validate(request);
        Event event = get(id);
        event.update(request.name().trim(), request.startDate(), request.endDate(),
                request.maxParticipants(), blankToNull(request.publicInfo()));
        ticketTypeCache.evict(); // navn, datoer, pladser og info vises på bestillingssiden
        return event;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Sletter et event med dets billettyper.
     *
     * Har eventet bestillinger, der afventer betaling eller er betalt, afvises det med 400, så rigtige
     * kunders bestillinger aldrig slettes ved en fejl. Annullerede og refunderede bestillinger slettes med.
     */
    @Transactional
    public void delete(int id) {
        // Lås eventet, så der ikke kan komme en ny bestilling ind, mens det slettes
        Event event = eventRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event " + id + " findes ikke"));

        List<CustomerOrder> orders = orderRepository.findByEventId(id);
        long active = orders.stream().filter(o -> TAKES_A_SEAT.contains(o.getStatus())).count();
        if (active > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, event.getName() + " har " + active + " "
                    + (active == 1 ? "aktiv bestilling" : "aktive bestillinger")
                    + " (afventer betaling eller betalt). Annullér eller refundér dem først.");
        }

        // Rækkefølgen betyder noget: bestillinger (og deres deltagere) peger på billettyperne,
        // og billettyperne peger på eventet.
        if (!orders.isEmpty()) {
            orderRepository.deletePaymentsForOrders(orders.stream().map(CustomerOrder::getId).toList());
            orderRepository.deleteAll(orders);
        }
        ticketTypeRepository.deleteAll(ticketTypeRepository.findByEventIdOrderByValidFrom(id));
        eventRepository.delete(event);
        ticketTypeCache.evict();
        log.info("Slettede eventet '{}' med {} annullerede/refunderede bestilling(er)", event.getName(), orders.size());
    }

    // Samme regel som CHECK (end_date >= start_date) i databasen, men med en læsbar fejlbesked
    private static void validate(EventRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slutdatoen kan ikke ligge før startdatoen");
        }
    }
}
