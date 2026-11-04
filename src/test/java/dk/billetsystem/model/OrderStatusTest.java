package dk.billetsystem.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Alle 16 kombinationer af statusskift: hvilke er tilladt, og hvilke er ikke? */
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            // Afventer betaling: kan betales eller annulleres
            "PENDING,   PENDING,   false",
            "PENDING,   PAID,      true",
            "PENDING,   CANCELLED, true",
            "PENDING,   REFUNDED,  false",
            // Betalt: kan kun refunderes
            "PAID,      PENDING,   false",
            "PAID,      PAID,      false",
            "PAID,      CANCELLED, false",
            "PAID,      REFUNDED,  true",
            // Annulleret: kan genåbnes
            "CANCELLED, PENDING,   true",
            "CANCELLED, PAID,      false",
            "CANCELLED, CANCELLED, false",
            "CANCELLED, REFUNDED,  false",
            // Refunderet: slutstatus
            "REFUNDED,  PENDING,   false",
            "REFUNDED,  PAID,      false",
            "REFUNDED,  CANCELLED, false",
            "REFUNDED,  REFUNDED,  false",
    })
    void canChangeTo(OrderStatus from, OrderStatus to, boolean allowed) {
        assertEquals(allowed, from.canChangeTo(to));
    }
}
