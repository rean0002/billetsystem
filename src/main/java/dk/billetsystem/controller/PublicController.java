package dk.billetsystem.controller;

import dk.billetsystem.dto.OrderRequest;
import dk.billetsystem.dto.OrderResponse;
import dk.billetsystem.dto.TicketTypeResponse;
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

import java.util.List;

/**
 * Det offentlige API, som bestillingssiden senere taler med.
 * En controller gør kun tre ting: modtager en forespørgsel, kalder en service, sender svaret tilbage.
 */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final TicketService ticketService;
    private final OrderService orderService;

    public PublicController(TicketService ticketService, OrderService orderService) {
        this.ticketService = ticketService;
        this.orderService = orderService;
    }

    /** GET /api/ticket-types/current: de billettyper, der kan købes lige nu. */
    @GetMapping("/ticket-types/current")
    public List<TicketTypeResponse> currentTicketTypes() {
        return ticketService.findCurrentTicketTypes().stream()
                .map(TicketTypeResponse::from)
                .toList();
    }

    /** POST /api/orders: opret en bestilling. Svarer med 201 Created. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody OrderRequest request) {
        return OrderResponse.from(orderService.createOrder(request));
    }
}
