package dk.billetsystem.model;

/**
 * Hvor langt en bestilling er nået. Værdierne er de samme som dem,
 * databasen tillader i kolonnen "status" i tabellen customer_order.
 */
public enum OrderStatus {
    PENDING,    // oprettet, men endnu ikke betalt
    PAID,       // betalt
    CANCELLED,  // annulleret
    REFUNDED;   // betalt og senere refunderet

    /**
     * Hvilke statusskift giver mening? Fx kan man ikke refundere en bestilling, der aldrig er betalt.
     * En annulleret bestilling kan genåbnes, hvis den blev annulleret ved en fejl.
     */
    public boolean canChangeTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == PAID || next == CANCELLED;
            case PAID -> next == REFUNDED;
            case CANCELLED -> next == PENDING;
            case REFUNDED -> false;
        };
    }
}
