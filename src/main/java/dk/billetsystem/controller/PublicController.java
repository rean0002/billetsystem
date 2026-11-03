package dk.billetsystem.controller;

import dk.billetsystem.dto.OrderRequest;
import dk.billetsystem.dto.OrderResponse;
import dk.billetsystem.dto.TicketTypeResponse;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.service.EventService;
import dk.billetsystem.service.PaymentSettingsService;
import dk.billetsystem.service.OrderService;
import dk.billetsystem.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Det offentlige API, som bestillingssiden senere taler med.
 * En controller gør kun tre ting: modtager en forespørgsel, kalder en service, sender svaret tilbage.
 */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final TicketService ticketService;
    private final OrderService orderService;
    private final EventService eventService;
    private final PaymentSettingsService paymentSettingsService;

    public PublicController(TicketService ticketService, OrderService orderService,
                            EventService eventService, PaymentSettingsService paymentSettingsService) {
        this.ticketService = ticketService;
        this.orderService = orderService;
        this.eventService = eventService;
        this.paymentSettingsService = paymentSettingsService;
    }

    /** GET /api/ticket-types/current: de billettyper, der kan købes lige nu, på tværs af events. */
    @GetMapping("/ticket-types/current")
    public List<TicketTypeResponse> currentTicketTypes() {
        List<TicketType> types = ticketService.findCurrentTicketTypes();
        // Ledige pladser regnes én gang pr. event, ikke én gang pr. billettype
        Map<Integer, Long> seatsLeft = new HashMap<>();
        for (TicketType t : types) {
            Event e = t.getEvent();
            if (!seatsLeft.containsKey(e.getId())) seatsLeft.put(e.getId(), eventService.seatsLeft(e));
        }
        return types.stream()
                .map(t -> TicketTypeResponse.from(t, seatsLeft.get(t.getEvent().getId())))
                .toList();
    }

    /** POST /api/orders: opret en bestilling. Svarer med 201 Created og oplysninger om, hvordan man betaler. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        CustomerOrder order = orderService.createOrder(request);
        return OrderResponse.from(order, paymentSettingsService.instructionsFor(order));
    }
}
