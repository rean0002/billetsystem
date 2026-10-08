package dk.billetsystem.service;

import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.TicketTypeRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Servicelaget holder forretningsreglerne. Her: "hvilke billetter kan købes lige nu?"
 * Reglen ligger ét sted, så både hjemmesiden og bestillingen bruger præcis samme svar.
 */
@Service
public class TicketService {

    private final TicketTypeRepository ticketTypeRepository;

    // Spring giver automatisk klassen det repository, den beder om (kaldes dependency injection)
    public TicketService(TicketTypeRepository ticketTypeRepository) {
        this.ticketTypeRepository = ticketTypeRepository;
    }

    public List<TicketType> findCurrentTicketTypes() {
        return ticketTypeRepository.findCurrent(OffsetDateTime.now());
    }
}
