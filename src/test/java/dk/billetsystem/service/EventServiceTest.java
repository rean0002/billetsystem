package dk.billetsystem.service;

import dk.billetsystem.TestData;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import dk.billetsystem.repository.EventRepository;
import dk.billetsystem.repository.ParticipantRepository;
import dk.billetsystem.repository.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kapacitet (må der bestilles eller genåbnes så mange pladser?) og sletning af events. */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    EventRepository eventRepository;
    @Mock
    ParticipantRepository participantRepository;
    @Mock
    CustomerOrderRepository orderRepository;
    @Mock
    TicketTypeRepository ticketTypeRepository;

    EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, participantRepository, orderRepository, ticketTypeRepository);
    }

    private void eventWith(Integer maxParticipants, long seatsTaken) {
        Event event = TestData.event(1, maxParticipants);
        when(eventRepository.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        if (maxParticipants != null) {
            when(participantRepository.countByEventAndStatus(eq(1), any())).thenReturn(seatsTaken);
        }
    }

    @Test
    void derErPlads() {
        eventWith(10, 8);
        assertDoesNotThrow(() -> eventService.reserveSeats(1, 2));
    }

    @Test
    void forFåPladserGiver400MedAntalLedige() {
        eventWith(10, 8);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.reserveSeats(1, 3));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Der er kun 2 pladser tilbage til Hobbymesse 2027", e.getReason());
    }

    @Test
    void udsolgtEvent() {
        eventWith(10, 10);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.reserveSeats(1, 1));
        assertEquals("Hobbymesse 2027 er desværre udsolgt", e.getReason());
    }

    @Test
    void ingenGrænseBetyderAltidPlads() {
        eventWith(null, 0);
        assertDoesNotThrow(() -> eventService.reserveSeats(1, 500));
        verify(participantRepository, never()).countByEventAndStatus(any(), any());
    }

    @Test
    void eventetLåsesFørPladserneTælles() {
        eventWith(10, 0);
        eventService.reserveSeats(1, 1);
        // findByIdForUpdate = SELECT ... FOR UPDATE, så samtidige bestillinger venter på hinanden
        verify(eventRepository).findByIdForUpdate(1);
        verify(eventRepository, never()).findById(any());
    }

    // ---- Sletning ----

    private Event eventToDelete(List<CustomerOrder> orders) {
        Event event = TestData.event(1, null);
        when(eventRepository.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        when(orderRepository.findByEventId(1)).thenReturn(orders);
        return event;
    }

    @Test
    void eventUdenBestillingerSlettesMedBilletyper() {
        Event event = eventToDelete(List.of());
        TicketType standard = TestData.ticketType(2, event, "Standard", 29500);
        when(ticketTypeRepository.findByEventIdOrderByValidFrom(1)).thenReturn(List.of(standard));

        eventService.delete(1);

        verify(ticketTypeRepository).deleteAll(List.of(standard));
        verify(eventRepository).delete(event);
        verify(orderRepository, never()).deleteAll(any());
    }

    @Test
    void aktiveBestillingerForhindrerSletning() {
        Event event = TestData.event(1, null);
        TicketType standard = TestData.ticketType(2, event, "Standard", 29500);
        CustomerOrder pending = TestData.order(5, standard, "Lene Mortensen");
        CustomerOrder paid = TestData.order(6, standard, "Pia Kristensen");
        paid.changeStatus(OrderStatus.PAID);
        when(eventRepository.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        when(orderRepository.findByEventId(1)).thenReturn(List.of(pending, paid));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.delete(1));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Hobbymesse 2027 har 2 aktive bestillinger (afventer betaling eller betalt). "
                + "Annullér eller refundér dem først.", e.getReason());
        verify(eventRepository, never()).delete(any());
    }

    @Test
    void annulleredeBestillingerSlettesMed() {
        Event event = TestData.event(1, null);
        TicketType standard = TestData.ticketType(2, event, "Standard", 29500);
        CustomerOrder cancelled = TestData.order(5, standard, "Lene Mortensen");
        cancelled.changeStatus(OrderStatus.CANCELLED);
        when(eventRepository.findByIdForUpdate(1)).thenReturn(Optional.of(event));
        when(orderRepository.findByEventId(1)).thenReturn(List.of(cancelled));
        when(ticketTypeRepository.findByEventIdOrderByValidFrom(1)).thenReturn(List.of(standard));

        eventService.delete(1);

        // Først betalinger og bestillinger, så billettyper, til sidst eventet
        InOrder inOrder = inOrder(orderRepository, ticketTypeRepository, eventRepository);
        inOrder.verify(orderRepository).deletePaymentsForOrders(List.of(5));
        inOrder.verify(orderRepository).deleteAll(List.of(cancelled));
        inOrder.verify(ticketTypeRepository).deleteAll(List.of(standard));
        inOrder.verify(eventRepository).delete(event);
    }

    @Test
    void ukendtEventGiver404() {
        when(eventRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> eventService.delete(99));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}
