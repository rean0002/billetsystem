package dk.billetsystem.service;

import dk.billetsystem.TestData;
import dk.billetsystem.dto.OrderRequest;
import dk.billetsystem.dto.OrderRequest.ParticipantRequest;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prisberegning og regler ved oprettelse af en bestilling. Databasen er erstattet af "mocks",
 * så testene kører hurtigt og uden PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    CustomerOrderRepository orderRepository;
    @Mock
    TicketService ticketService;
    @Mock
    EventService eventService;
    @Mock
    ApplicationEventPublisher events;
    @Mock
    TicketTypeCache ticketTypeCache;

    OrderService orderService;

    Event event = TestData.event(1, null);
    TicketType earlyBird = TestData.ticketType(1, event, "Early Bird", 19500);
    TicketType standard = TestData.ticketType(2, event, "Standard", 29500);

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, ticketService, eventService, events, ticketTypeCache);
    }

    /** Når bestillingen gemmes, giver "databasen" den et id. */
    private void saveGivesId() {
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
            CustomerOrder order = inv.getArgument(0);
            ReflectionTestUtils.setField(order, "id", 42);
            return order;
        });
    }

    private static OrderRequest request(Integer... ticketTypeIds) {
        List<ParticipantRequest> participants = new ArrayList<>();
        for (int i = 0; i < ticketTypeIds.length; i++) {
            participants.add(new ParticipantRequest("Deltager " + (i + 1), ticketTypeIds[i], false, false));
        }
        return new OrderRequest("Lene Mortensen", "lene.mortensen@example.com", null, null, null, participants);
    }

    @Test
    void earlyBirdPrisBrugesMensDenGælder() {
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(earlyBird, standard));
        saveGivesId();

        CustomerOrder order = orderService.createOrder(request(1));

        assertEquals(19500, order.getTotalOre());
        assertEquals(19500, order.getParticipants().get(0).getPriceOre());
    }

    @Test
    void normalprisOgSamletPrisForFlereDeltagere() {
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(earlyBird, standard));
        saveGivesId();

        CustomerOrder order = orderService.createOrder(request(2, 2, 1));

        assertEquals(29500 + 29500 + 19500, order.getTotalOre());
    }

    @Test
    void udløbetBillettypeAfvises() {
        // Early bird er udløbet, så den er ikke blandt dem, der kan købes nu
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(standard));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> orderService.createOrder(request(1)));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertTrue(e.getReason().contains("kan ikke købes lige nu"));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void kapacitetTjekkesMedAntalDeltagere() {
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(standard));
        saveGivesId();

        orderService.createOrder(request(2, 2));

        verify(eventService).reserveSeats(1, 2);
        verify(ticketTypeCache).evict(); // ledige pladser er ændret
    }

    @Test
    void udsolgtEventGemmerIkkeBestillingen() {
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(standard));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hobbymesse 2027 er desværre udsolgt"))
                .when(eventService).reserveSeats(anyInt(), anyInt());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> orderService.createOrder(request(2)));

        assertEquals("Hobbymesse 2027 er desværre udsolgt", e.getReason());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void billetterTilToEventsAfvises() {
        Event other = TestData.event(2, null);
        TicketType otherType = TestData.ticketType(3, other, "Standard", 29500);
        when(ticketService.findCurrentTicketTypes()).thenReturn(List.of(standard, otherType));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> orderService.createOrder(request(2, 3)));

        assertTrue(e.getReason().contains("ét event"));
        verify(eventService, never()).reserveSeats(anyInt(), anyInt());
    }
}
