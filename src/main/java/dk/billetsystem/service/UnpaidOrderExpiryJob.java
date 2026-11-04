package dk.billetsystem.service;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.PaymentSettings;
import dk.billetsystem.repository.CustomerOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Annullerer bestillinger, der stadig afventer betaling, når betalingsfristen er overskredet.
 * Så frigives pladserne til andre, uden at arrangørerne skal gøre det i hånden.
 *
 * Fristen er bestillingsdatoen plus antal dage fra Admin -> Betaling (PaymentSettingsService.dueDate).
 * "Overskredet" betyder, at sidste betalingsdag er gået, præcis som markeringen "Frist overskredet" på adminsiden.
 */
@Component
public class UnpaidOrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(UnpaidOrderExpiryJob.class);

    private final CustomerOrderRepository orderRepository;
    private final PaymentSettingsService paymentSettingsService;
    private final Clock clock;

    public UnpaidOrderExpiryJob(CustomerOrderRepository orderRepository,
                                PaymentSettingsService paymentSettingsService,
                                Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentSettingsService = paymentSettingsService;
        this.clock = clock;
    }

    /** Kører hver hele time (sekund 0, minut 0). Returnerer antallet af annullerede bestillinger. */
    @Scheduled(cron = "0 0 * * * *", zone = "Europe/Copenhagen")
    @Transactional
    public int cancelOverdueOrders() {
        PaymentSettings settings = paymentSettingsService.get();
        LocalDate today = LocalDate.now(clock.withZone(StatisticsService.DANISH_TIME));

        int cancelled = 0;
        for (CustomerOrder order : orderRepository.findByStatus(OrderStatus.PENDING)) {
            if (today.isAfter(PaymentSettingsService.dueDate(order, settings))) {
                order.changeStatus(OrderStatus.CANCELLED);
                cancelled++;
            }
        }
        if (cancelled > 0) {
            log.info("Annullerede {} ubetalte bestilling(er) med overskredet betalingsfrist", cancelled);
        }
        return cancelled;
    }
}
