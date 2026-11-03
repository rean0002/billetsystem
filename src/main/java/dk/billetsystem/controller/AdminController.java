package dk.billetsystem.controller;

import dk.billetsystem.dto.AdminEventResponse;
import dk.billetsystem.dto.AdminOrderResponse;
import dk.billetsystem.dto.AdminParticipantResponse;
import dk.billetsystem.dto.AdminTicketTypeResponse;
import dk.billetsystem.dto.EventRequest;
import dk.billetsystem.dto.OverviewResponse;
import dk.billetsystem.dto.PaymentSettingsRequest;
import dk.billetsystem.dto.PaymentSettingsResponse;
import dk.billetsystem.dto.StatusChangeRequest;
import dk.billetsystem.dto.TicketTypeRequest;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.service.AdminOrderService;
import dk.billetsystem.service.EventService;
import dk.billetsystem.service.PaymentSettingsService;
import dk.billetsystem.service.StatisticsService;
import dk.billetsystem.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Endpoints til login og adminsiden. Alt under /api/admin kræver login (se SecurityConfig).
 *
 * De fleste endpoints tager ?eventId=. Uden den bruges det næste kommende event.
 */
@RestController
public class AdminController {

    private final AdminOrderService adminOrderService;
    private final StatisticsService statisticsService;
    private final TicketService ticketService;
    private final EventService eventService;
    private final PaymentSettingsService paymentSettingsService;

    public AdminController(AdminOrderService adminOrderService, StatisticsService statisticsService,
                           TicketService ticketService, EventService eventService,
                           PaymentSettingsService paymentSettingsService) {
        this.adminOrderService = adminOrderService;
        this.statisticsService = statisticsService;
        this.ticketService = ticketService;
        this.eventService = eventService;
        this.paymentSettingsService = paymentSettingsService;
    }

    /**
     * GET /api/csrf: giver login- og adminsiden den CSRF-nøgle, de skal sende med.
     * Formularer sender den som et felt (parameterName), JavaScript som en header (headerName).
     * (En almindelig HTML-fil kan ikke selv kende nøglen, så den henter den her.)
     */
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("parameterName", token.getParameterName(), "headerName", token.getHeaderName(), "token", token.getToken());
    }

    /** GET /api/admin/me: hvem er logget ind? */
    @GetMapping("/api/admin/me")
    public Map<String, String> me(@AuthenticationPrincipal UserDetails user) {
        return Map.of("username", user.getUsername());
    }

    /** GET /api/admin/overview?eventId=2: alle tal til forsiden. */
    @GetMapping("/api/admin/overview")
    public OverviewResponse overview(@RequestParam(required = false) Integer eventId) {
        return statisticsService.overview(eventId);
    }

    // ---- Betalingsoplysninger ----

    @GetMapping("/api/admin/payment-settings")
    public PaymentSettingsResponse paymentSettings() {
        return PaymentSettingsResponse.from(paymentSettingsService.get());
    }

    @PutMapping("/api/admin/payment-settings")
    public PaymentSettingsResponse updatePaymentSettings(@Valid @RequestBody PaymentSettingsRequest request) {
        return PaymentSettingsResponse.from(paymentSettingsService.update(request));
    }

    // ---- Events ----

    /** Alle events med salgstal, nyeste først. isDefault markerer det event, der vises, når man ikke har valgt et. */
    @GetMapping("/api/admin/events")
    public List<AdminEventResponse> events() {
        return statisticsService.eventsWithSales();
    }

    /** Opretter et event og svarer med den opdaterede liste. */
    @PostMapping("/api/admin/events")
    public List<AdminEventResponse> createEvent(@Valid @RequestBody EventRequest request) {
        eventService.create(request);
        return statisticsService.eventsWithSales();
    }

    @PutMapping("/api/admin/events/{id}")
    public List<AdminEventResponse> updateEvent(@PathVariable int id, @Valid @RequestBody EventRequest request) {
        eventService.update(id, request);
        return statisticsService.eventsWithSales();
    }

    // ---- Bestillinger ----

    /** GET /api/admin/orders?eventId=2&status=PENDING&search=anna */
    @GetMapping("/api/admin/orders")
    public List<AdminOrderResponse> orders(@RequestParam(required = false) Integer eventId,
                                           @RequestParam(required = false) OrderStatus status,
                                           @RequestParam(required = false) String search) {
        return adminOrderService.findOrders(eventId, status, search);
    }

    @GetMapping("/api/admin/orders/{id}")
    public AdminOrderResponse order(@PathVariable int id) {
        return adminOrderService.findOrder(id);
    }

    /** POST /api/admin/orders/5/status med {"status": "PAID"}, fx når bankoverførslen er modtaget. */
    @PostMapping("/api/admin/orders/{id}/status")
    public AdminOrderResponse changeStatus(@PathVariable int id, @Valid @RequestBody StatusChangeRequest request) {
        return adminOrderService.changeStatus(id, request.status());
    }

    @GetMapping("/api/admin/orders.csv")
    public ResponseEntity<byte[]> ordersCsv(@RequestParam(required = false) Integer eventId) {
        return csv("bestillinger", adminOrderService.ordersCsv(eventId));
    }

    // ---- Deltagere ----

    /** GET /api/admin/participants?eventId=2&search=anna&ticketTypeId=2&includeCancelled=false */
    @GetMapping("/api/admin/participants")
    public List<AdminParticipantResponse> participants(@RequestParam(required = false) Integer eventId,
                                                       @RequestParam(required = false) String search,
                                                       @RequestParam(required = false) Integer ticketTypeId,
                                                       @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return adminOrderService.findParticipants(eventId, search, ticketTypeId, includeCancelled);
    }

    @GetMapping("/api/admin/participants.csv")
    public ResponseEntity<byte[]> participantsCsv(@RequestParam(required = false) Integer eventId) {
        return csv("deltagerliste", adminOrderService.participantsCsv(eventId));
    }

    // ---- Billettyper ----

    @GetMapping("/api/admin/ticket-types")
    public List<AdminTicketTypeResponse> ticketTypes(@RequestParam(required = false) Integer eventId) {
        return statisticsService.ticketTypesWithSales(eventId);
    }

    /** POST /api/admin/ticket-types?eventId=2: opretter en billettype til eventet og svarer med eventets billettyper. */
    @PostMapping("/api/admin/ticket-types")
    public List<AdminTicketTypeResponse> createTicketType(@RequestParam(required = false) Integer eventId,
                                                          @Valid @RequestBody TicketTypeRequest request) {
        int id = eventService.getOrDefault(eventId).getId();
        ticketService.create(id, request);
        return statisticsService.ticketTypesWithSales(id);
    }

    /** Retter en billettype og svarer med billettyperne for samme event. */
    @PutMapping("/api/admin/ticket-types/{id}")
    public List<AdminTicketTypeResponse> updateTicketType(@PathVariable int id, @Valid @RequestBody TicketTypeRequest request) {
        int eventId = ticketService.update(id, request).getEvent().getId();
        return statisticsService.ticketTypesWithSales(eventId);
    }

    // -----------------------------------------------------------------

    /** Sender en CSV-fil, som browseren gemmer som fx "deltagerliste-2026-11-03.csv". */
    private static ResponseEntity<byte[]> csv(String name, byte[] content) {
        String filename = name + "-" + LocalDate.now(StatisticsService.DANISH_TIME) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(content);
    }
}
