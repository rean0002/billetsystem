package dk.billetsystem.service;

import dk.billetsystem.dto.TicketTypeRequest;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.EventRepository;
import dk.billetsystem.repository.TicketTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Servicelaget holder forretningsreglerne. Her: "hvilke billetter kan købes lige nu?"
 * Reglen ligger ét sted, så både hjemmesiden og bestillingen bruger præcis samme svar.
 */
@Service
public class TicketService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;

    // Spring giver automatisk klassen de repositories, den beder om (kaldes dependency injection)
    private final TicketTypeCache ticketTypeCache;

    public TicketService(TicketTypeRepository ticketTypeRepository, EventRepository eventRepository,
                         TicketTypeCache ticketTypeCache) {
        this.ticketTypeRepository = ticketTypeRepository;
        this.eventRepository = eventRepository;
        this.ticketTypeCache = ticketTypeCache;
    }

    public List<TicketType> findCurrentTicketTypes() {
        return ticketTypeRepository.findCurrent(OffsetDateTime.now());
    }

    /** Opretter en billettype til et event. */
    @Transactional
    public TicketType create(int eventId, TicketTypeRequest request) {
        validatePeriod(request);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event " + eventId + " findes ikke"));
        ticketTypeCache.evict(); // bestillingssiden skal vise den nye billettype
        return ticketTypeRepository.save(new TicketType(event, request.name().trim(), request.priceOre(),
                request.validFrom(), request.validTo(), request.active()));
    }

    @Transactional
    public TicketType update(int id, TicketTypeRequest request) {
        validatePeriod(request);
        TicketType type = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Billettype " + id + " findes ikke"));
        type.update(request.name().trim(), request.priceOre(), request.validFrom(), request.validTo(), request.active());
        ticketTypeCache.evict();
        return type;
    }

    // Samme regel som CHECK (valid_to > valid_from) i databasen, men med en læsbar fejlbesked
    private static void validatePeriod(TicketTypeRequest request) {
        if (request.validTo() != null && !request.validTo().isAfter(request.validFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slutdatoen skal ligge efter startdatoen");
        }
    }
}
