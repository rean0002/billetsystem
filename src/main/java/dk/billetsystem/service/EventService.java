package dk.billetsystem.service;

import dk.billetsystem.dto.EventRequest;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.repository.EventRepository;
import dk.billetsystem.repository.ParticipantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * Events, fx "Hobbymesse april 2027" og "Hobbymesse oktober 2027".
 * Hver billettype hører til ét event, og en bestilling indeholder billetter til ét event.
 */
@Service
public class EventService {

    /** Bestillinger med disse statusser optager en plads. Annullerede og refunderede gør ikke. */
    public static final EnumSet<OrderStatus> TAKES_A_SEAT = EnumSet.of(OrderStatus.PAID, OrderStatus.PENDING);

    private final EventRepository eventRepository;
    private final ParticipantRepository participantRepository;

    public EventService(EventRepository eventRepository, ParticipantRepository participantRepository) {
        this.eventRepository = eventRepository;
        this.participantRepository = participantRepository;
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

    /** Ledige pladser, eller null hvis eventet ikke har en grænse. */
    public Long seatsLeft(Event event) {
        if (event.getMaxParticipants() == null) return null;
        return Math.max(0, event.getMaxParticipants() - seatsTaken(event));
    }

    @Transactional
    public Event create(EventRequest request) {
        validate(request);
        return eventRepository.save(new Event(request.name().trim(), request.startDate(), request.endDate(), request.maxParticipants()));
    }

    @Transactional
    public Event update(int id, EventRequest request) {
        validate(request);
        Event event = get(id);
        event.update(request.name().trim(), request.startDate(), request.endDate(), request.maxParticipants());
        return event;
    }

    // Samme regel som CHECK (end_date >= start_date) i databasen, men med en læsbar fejlbesked
    private static void validate(EventRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slutdatoen kan ikke ligge før startdatoen");
        }
    }
}
