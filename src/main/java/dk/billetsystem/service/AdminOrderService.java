package dk.billetsystem.service;

import dk.billetsystem.dto.AdminOrderResponse;
import dk.billetsystem.dto.AdminParticipantResponse;
import dk.billetsystem.dto.SeatingResponse;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.Participant;
import dk.billetsystem.repository.CustomerOrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Bestillinger og deltagerliste til adminsiden.
 *
 * Søgning og filtrering sker i Java i stedet for i SQL. Et typisk event har nogle hundrede
 * bestillinger, så det er hurtigt nok og meget enklere. Bliver det til tusindvis, kan det flyttes til SQL.
 */
@Service
public class AdminOrderService {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CustomerOrderRepository orderRepository;
    private final EventService eventService;
    private final PaymentSettingsService paymentSettingsService;

    public AdminOrderService(CustomerOrderRepository orderRepository, EventService eventService,
                             PaymentSettingsService paymentSettingsService) {
        this.orderRepository = orderRepository;
        this.eventService = eventService;
        this.paymentSettingsService = paymentSettingsService;
    }

    /** En bestilling, som adminsiden ser den, inkl. betalingsfrist. */
    public AdminOrderResponse toResponse(CustomerOrder order) {
        LocalDate due = PaymentSettingsService.dueDate(order, paymentSettingsService.get());
        return AdminOrderResponse.from(order, due, LocalDate.now(StatisticsService.DANISH_TIME));
    }

    /** Bestillinger til et event, nyeste først. status og search er valgfrie. */
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> findOrders(Integer eventId, OrderStatus status, String search) {
        return ordersFor(eventId).stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> matches(search, o.getContactName(), o.getEmail(), o.getPhone(), String.valueOf(o.getId()))
                        || o.getParticipants().stream().anyMatch(p -> matches(search, p.getFullName())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse findOrder(int id) {
        return toResponse(getOrder(id));
    }

    @Transactional
    public AdminOrderResponse changeStatus(int id, OrderStatus newStatus) {
        CustomerOrder order = getOrder(id);
        try {
            order.changeStatus(newStatus);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return toResponse(order);
    }

    /**
     * Deltagerlisten, sorteret efter navn. Annullerede og refunderede bestillinger er udeladt,
     * medmindre includeCancelled er true.
     */
    @Transactional(readOnly = true)
    public List<AdminParticipantResponse> findParticipants(Integer eventId, String search, Integer ticketTypeId, boolean includeCancelled) {
        return participants(eventId, includeCancelled)
                .filter(p -> ticketTypeId == null || p.getTicketType().getId().equals(ticketTypeId))
                .filter(p -> matches(search, p.getFullName(), p.getCustomerOrder().getContactName(), p.getCustomerOrder().getEmail()))
                .sorted(Comparator.comparing(Participant::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(AdminParticipantResponse::from)
                .toList();
    }

    /**
     * Overblik til bordplanen. Navne, som grupper vil sidde med, slås op blandt eventets deltagere,
     * så arrangørerne kan se, om personen har en billet, og i hvilken bestilling.
     */
    @Transactional(readOnly = true)
    public SeatingResponse seating(Integer eventId) {
        List<CustomerOrder> active = ordersFor(eventId).stream()
                .filter(o -> EventService.TAKES_A_SEAT.contains(o.getStatus()))
                .toList();

        // Navn -> bestilling, til at finde dem, en gruppe vil sidde med
        Map<String, Integer> orderByName = new HashMap<>();
        for (CustomerOrder o : active) {
            for (Participant p : o.getParticipants()) orderByName.putIfAbsent(nameKey(p.getFullName()), o.getId());
        }

        List<SeatingResponse.Group> groups = active.stream()
                .filter(o -> o.getParticipants().size() > 1 || !o.getSeatWithNames().isEmpty())
                // Største grupper først (deltagere + dem, de vil sidde med)
                .sorted(Comparator.comparingInt((CustomerOrder o) -> o.getParticipants().size() + o.getSeatWithNames().size()).reversed())
                .map(o -> new SeatingResponse.Group(o.getId(), o.getContactName(), o.getStatus().name(),
                        o.getParticipants().stream().map(Participant::getFullName).toList(),
                        o.getSeatWithNames().stream()
                                .map(n -> new SeatingResponse.Wish(n, orderByName.get(nameKey(n))))
                                .toList()))
                .toList();

        List<Participant> all = active.stream().flatMap(o -> o.getParticipants().stream())
                .sorted(Comparator.comparing(Participant::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new SeatingResponse(groups,
                all.stream().filter(Participant::isBeginnerTable).map(AdminOrderService::person).toList(),
                all.stream().filter(Participant::isNeedsTableEnd).map(AdminOrderService::person).toList(),
                all.size());
    }

    private static SeatingResponse.Person person(Participant p) {
        CustomerOrder o = p.getCustomerOrder();
        return new SeatingResponse.Person(p.getFullName(), o.getId(), o.getStatus().name(), o.getContactName());
    }

    /** "  anna   ANDERSEN " og "Anna Andersen" skal regnes som samme navn. */
    private static String nameKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // -----------------------------------------------------------------
    // CSV-eksport. Semikolon og "BOM" i starten, så dansk Excel åbner filen med æøå og rigtige kolonner.
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public byte[] participantsCsv(Integer eventId) {
        StringBuilder csv = new StringBuilder();
        csvLine(csv, "Navn", "Billettype", "Pris (kr.)", "Status", "Bestilling nr.", "Kontaktperson", "E-mail", "Telefon", "Bestilt",
                "Begynder-/singlebord", "Bordende (helbred)", "Vil sidde med");
        participants(eventId, false)
                .sorted(Comparator.comparing(Participant::getFullName, String.CASE_INSENSITIVE_ORDER))
                .forEach(p -> {
                    CustomerOrder o = p.getCustomerOrder();
                    csvLine(csv, p.getFullName(), p.getTicketType().getName(), kroner(p.getPriceOre()), danish(o.getStatus()),
                            String.valueOf(o.getId()), o.getContactName(), o.getEmail(), o.getPhone(), time(o.getCreatedAt()),
                            p.isBeginnerTable() ? "Ja" : "", p.isNeedsTableEnd() ? "Ja" : "",
                            String.join(", ", o.getSeatWithNames()));
                });
        return withBom(csv);
    }

    @Transactional(readOnly = true)
    public byte[] ordersCsv(Integer eventId) {
        StringBuilder csv = new StringBuilder();
        csvLine(csv, "Bestilling nr.", "Bestilt", "Status", "Betalt", "Kontaktperson", "E-mail", "Telefon", "Antal billetter", "Beløb (kr.)", "Besked");
        for (CustomerOrder o : ordersFor(eventId)) {
            csvLine(csv, String.valueOf(o.getId()), time(o.getCreatedAt()), danish(o.getStatus()), time(o.getPaidAt()),
                    o.getContactName(), o.getEmail(), o.getPhone(), String.valueOf(o.getParticipants().size()),
                    kroner(o.getTotalOre()), o.getNote());
        }
        return withBom(csv);
    }

    // -----------------------------------------------------------------

    private CustomerOrder getOrder(int id) {
        return orderRepository.findWithParticipantsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bestilling " + id + " findes ikke"));
    }

    private List<CustomerOrder> ordersFor(Integer eventId) {
        Event event = eventService.getOrDefault(eventId);
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> StatisticsService.belongsTo(o, event))
                .toList();
    }

    private Stream<Participant> participants(Integer eventId, boolean includeCancelled) {
        return ordersFor(eventId).stream()
                .filter(o -> includeCancelled || o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.PAID)
                .flatMap(o -> o.getParticipants().stream());
    }

    /** Indeholder et af felterne søgeteksten? (Uden søgetekst matcher alt.) */
    private static boolean matches(String search, String... fields) {
        if (search == null || search.isBlank()) return true;
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return Stream.of(fields).anyMatch(f -> f != null && f.toLowerCase(Locale.ROOT).contains(needle));
    }

    static String danish(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Afventer betaling";
            case PAID -> "Betalt";
            case CANCELLED -> "Annulleret";
            case REFUNDED -> "Refunderet";
        };
    }

    private static String kroner(int ore) {
        return String.format(Locale.of("da", "DK"), "%.2f", ore / 100.0);
    }

    private static String time(OffsetDateTime t) {
        return t == null ? "" : t.atZoneSameInstant(StatisticsService.DANISH_TIME).format(CSV_TIME);
    }

    private static void csvLine(StringBuilder csv, String... values) {
        csv.append(String.join(";", Stream.of(values).map(AdminOrderService::csvValue).toList())).append("\r\n");
    }

    /** Sætter anførselstegn om værdien, så semikolon, linjeskift og " i fx en besked ikke ødelægger kolonnerne. */
    private static String csvValue(String value) {
        if (value == null) return "";
        // Excel tolker en celle, der starter med = + - @, som en formel. Et ' foran forhindrer det.
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static byte[] withBom(StringBuilder csv) {
        return ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
    }
}
