package dk.billetsystem;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.Participant;
import dk.billetsystem.model.TicketType;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Små hjælpere til at lave testdata. Id'er sættes normalt af databasen,
 * så her sættes de direkte med ReflectionTestUtils.
 */
public final class TestData {

    private TestData() {
    }

    public static Event event(int id, Integer maxParticipants) {
        Event event = new Event("Hobbymesse 2027", LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 4), maxParticipants, null);
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    public static TicketType ticketType(int id, Event event, String name, int priceOre) {
        TicketType type = new TicketType(event, name, priceOre, OffsetDateTime.parse("2026-10-01T00:00:00+02:00"), null, true);
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    /** En bestilling med én deltager pr. navn, alle med samme billettype. */
    public static CustomerOrder order(int id, TicketType type, String... names) {
        CustomerOrder order = new CustomerOrder(names[0], "kontakt@example.com", null, null, null);
        for (String name : names) {
            order.addParticipant(new Participant(type, name, type.getPriceOre(), false, false));
        }
        order.setTotalOre(type.getPriceOre() * names.length);
        ReflectionTestUtils.setField(order, "id", id);
        return order;
    }
}
