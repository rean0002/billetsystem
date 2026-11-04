package dk.billetsystem.service;

import dk.billetsystem.TestData;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Ubetalte bestillinger annulleres, når betalingsfristen (7 dage) er overskredet. */
@ExtendWith(MockitoExtension.class)
class UnpaidOrderExpiryJobTest {

    @Mock
    CustomerOrderRepository orderRepository;
    @Mock
    PaymentSettingsService paymentSettingsService;

    // "Nu" er 20. november 2026 kl. 12 dansk tid
    Clock clock = Clock.fixed(Instant.parse("2026-11-20T11:00:00Z"), ZoneOffset.UTC);
    UnpaidOrderExpiryJob job;
    TicketType standard = TestData.ticketType(2, TestData.event(1, null), "Standard", 29500);

    @BeforeEach
    void setUp() {
        PaymentSettings settings = mock(PaymentSettings.class);
        // lenient: bruges ikke, når der ingen bestillinger er
        lenient().when(settings.getPaymentDays()).thenReturn(7);
        when(paymentSettingsService.get()).thenReturn(settings);
        job = new UnpaidOrderExpiryJob(orderRepository, paymentSettingsService, clock);
    }

    private CustomerOrder pendingOrderFrom(String createdAt) {
        CustomerOrder order = TestData.order(1, standard, "Lene Mortensen");
        ReflectionTestUtils.setField(order, "createdAt", OffsetDateTime.parse(createdAt));
        return order;
    }

    @Test
    void annullererKunBestillingerOverFristen() {
        CustomerOrder overdue = pendingOrderFrom("2026-11-12T10:00:00+01:00");   // frist 19. nov.: overskredet
        CustomerOrder lastDay = pendingOrderFrom("2026-11-13T10:00:00+01:00");   // frist 20. nov.: i dag, ikke overskredet
        CustomerOrder fresh = pendingOrderFrom("2026-11-19T10:00:00+01:00");     // frist 26. nov.
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(overdue, lastDay, fresh));

        int cancelled = job.cancelOverdueOrders();

        assertEquals(1, cancelled);
        assertEquals(OrderStatus.CANCELLED, overdue.getStatus());
        assertEquals(OrderStatus.PENDING, lastDay.getStatus());
        assertEquals(OrderStatus.PENDING, fresh.getStatus());
    }

    @Test
    void fristenRegnesIDanskTid() {
        // Bestilt 12. nov. kl. 23.30 dansk tid (22.30 UTC): fristen er 19. nov., ikke 18. nov.
        CustomerOrder lateEvening = pendingOrderFrom("2026-11-12T22:30:00Z");
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of(lateEvening));

        assertEquals(1, job.cancelOverdueOrders());
    }

    @Test
    void ingenBestillingerIngenAnnulleringer() {
        when(orderRepository.findByStatus(OrderStatus.PENDING)).thenReturn(List.of());
        assertEquals(0, job.cancelOverdueOrders());
    }
}
