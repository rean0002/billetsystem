package dk.billetsystem.service;

import dk.billetsystem.dto.OrderRequest;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Participant;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final TicketService ticketService;

    public OrderService(CustomerOrderRepository orderRepository, TicketService ticketService) {
        this.orderRepository = orderRepository;
        this.ticketService = ticketService;
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
                request.note());

        int total = 0;
        for (OrderRequest.ParticipantRequest p : request.participants()) {
            TicketType type = available.get(p.ticketTypeId());
            if (type == null) {
                // Typen findes ikke, er slået fra, eller perioden er udløbet (fx early bird)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Billettypen " + p.ticketTypeId() + " findes ikke eller kan ikke købes lige nu");
            }
            // Prisen tages fra databasen, aldrig fra det, hjemmesiden sender
            order.addParticipant(new Participant(type, p.fullName().trim(), type.getPriceOre()));
            total += type.getPriceOre();
        }
        order.setTotalOre(total);

        return orderRepository.save(order);
    }
}
