package dk.billetsystem.mail;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sender mails, når en bestilling er oprettet eller betalt.
 *
 * AFTER_COMMIT betyder, at mailen først sendes, når bestillingen faktisk er gemt.
 * Går noget galt, og bestillingen ikke bliver gemt, får kunden heller ingen mail.
 */
@Component
public class OrderMailListener {

    private final OrderMailService mailService;

    public OrderMailListener(OrderMailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderMailEvents.OrderCreated event) {
        mailService.sendConfirmationInBackground(event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderMailEvents.OrderPaid event) {
        mailService.sendPaymentReceivedInBackground(event.orderId());
    }
}
