package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Én deltager = én billet. Svarer til tabellen "participant".
 *
 * priceOre er en KOPI af prisen på købstidspunktet. Hvis billetprisen ændres senere,
 * ændrer det ikke, hvad denne deltager faktisk betalte.
 */
@Entity
@Table(name = "participant")
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder customerOrder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_type_id")
    private TicketType ticketType;

    private String fullName;
    private int priceOre;

    protected Participant() {
    }

    public Participant(TicketType ticketType, String fullName, int priceOre) {
        this.ticketType = ticketType;
        this.fullName = fullName;
        this.priceOre = priceOre;
    }

    public Integer getId() {
        return id;
    }

    public CustomerOrder getCustomerOrder() {
        return customerOrder;
    }

    // Uden "public": kun CustomerOrder (i samme pakke) må sætte ordren, via addParticipant()
    void setCustomerOrder(CustomerOrder customerOrder) {
        this.customerOrder = customerOrder;
    }

    public TicketType getTicketType() {
        return ticketType;
    }

    public String getFullName() {
        return fullName;
    }

    public int getPriceOre() {
        return priceOre;
    }
}
