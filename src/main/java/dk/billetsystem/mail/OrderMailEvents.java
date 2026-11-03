package dk.billetsystem.mail;

/**
 * Hændelser, der udløser en mail. De sendes af OrderService og AdminOrderService og
 * opfanges af OrderMailListener, først når bestillingen er gemt i databasen.
 */
public final class OrderMailEvents {

    private OrderMailEvents() {
    }

    /** En kunde har oprettet en bestilling. */
    public record OrderCreated(int orderId) {
    }

    /** Admin har markeret en bestilling som betalt. */
    public record OrderPaid(int orderId) {
    }
}
