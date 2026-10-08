package dk.billetsystem.model;

/**
 * Hvor langt en bestilling er nået. Værdierne er de samme som dem,
 * databasen tillader i kolonnen "status" i tabellen customer_order.
 */
public enum OrderStatus {
    PENDING,    // oprettet, men endnu ikke betalt
    PAID,       // betalt
    CANCELLED,  // annulleret
    REFUNDED    // betalt og senere refunderet
}
