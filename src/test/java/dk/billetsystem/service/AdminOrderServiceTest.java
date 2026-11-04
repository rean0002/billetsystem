package dk.billetsystem.service;

import dk.billetsystem.TestData;
import dk.billetsystem.dto.AdminOrderResponse;
import dk.billetsystem.mail.OrderMailEvents;
import dk.billetsystem.mail.OrderMailService;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.PaymentSettings;
import dk.billetsystem.model.TicketType;
import dk.billetsystem.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Statusskift på adminsiden, især at en genåbnet bestilling ikke kan overbooke eventet. */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    CustomerOrderRepository orderRepository;
    @Mock
    EventService eventService;
    @Mock
    PaymentSettingsService paymentSettingsService;
    @Mock
    OrderMailService mailService;
    @Mock
    ApplicationEventPublisher events;

    AdminOrderService service;
    CustomerOrder order;

    @BeforeEach
    void setUp() {
        service = new AdminOrderService(orderRepository, eventService, paymentSettingsService, mailService, events);

        TicketType standard = TestData.ticketType(2, TestData.event(1, 10), "Standard", 29500);
        order = TestData.order(7, standard, "Lene Mortensen", "Tina Kristensen");
        when(orderRepository.findWithParticipantsById(7)).thenReturn(Optional.of(order));

        // Bruges kun til at beregne betalingsfristen i svaret
        PaymentSettings settings = mock(PaymentSettings.class);
        lenient().when(settings.getPaymentDays()).thenReturn(7);
        lenient().when(paymentSettingsService.get()).thenReturn(settings);
    }

    @Test
    void genåbningTjekkerKapacitetForAlleDeltagere() {
        order.changeStatus(OrderStatus.CANCELLED);

        AdminOrderResponse response = service.changeStatus(7, OrderStatus.PENDING);

        verify(eventService).reserveSeats(1, 2);
        assertEquals("PENDING", response.status());
    }

    @Test
    void genåbningAfvisesNårEventetErFyldt() {
        order.changeStatus(OrderStatus.CANCELLED);
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der er kun 1 plads tilbage til Hobbymesse 2027"))
                .when(eventService).reserveSeats(1, 2);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.changeStatus(7, OrderStatus.PENDING));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        assertEquals("Der er kun 1 plads tilbage til Hobbymesse 2027", e.getReason());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void betalingTjekkerIkkeKapacitetIgen() {
        // PENDING optager allerede pladserne, så PENDING -> PAID må ikke tælle dem en gang til
        service.changeStatus(7, OrderStatus.PAID);

        verify(eventService, never()).reserveSeats(anyInt(), anyInt());
        verify(events).publishEvent(new OrderMailEvents.OrderPaid(7));
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void ulovligtSkiftAfvisesUdenKapacitetstjek() {
        order.changeStatus(OrderStatus.CANCELLED);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.changeStatus(7, OrderStatus.PAID));

        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
        verify(eventService, never()).reserveSeats(anyInt(), anyInt());
    }
}
