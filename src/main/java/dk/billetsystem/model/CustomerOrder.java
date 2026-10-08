package dk.billetsystem.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Én bestilling. Den kan indeholde flere deltagere (Participant).
 * Svarer til tabellen "customer_order".
 * (Klassen hedder ikke "Order", fordi "order" er et reserveret ord i SQL.)
 */
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String contactName;
    private String email;
    private String phone;
    private String note;

    // Gemmes som tekst i databasen ("PENDING", "PAID" ...)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    private int totalOre;
    private String stripeSessionId;

    // Vi sætter tidspunktet selv, når objektet oprettes. (Databasens default now() bruges ikke,
    // fordi Hibernate altid sender alle kolonner med i sin INSERT.)
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime paidAt;

    // En bestilling har mange deltagere. "mappedBy" peger på feltet i Participant, der ejer relationen.
    // CascadeType.ALL = når vi gemmer en ordre, gemmes dens deltagere automatisk med.
    @OneToMany(mappedBy = "customerOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Participant> participants = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(String contactName, String email, String phone, String note) {
        this.contactName = contactName;
        this.email = email;
        this.phone = phone;
        this.note = note;
    }

    /** Tilføjer en deltager og sørger for, at deltageren også peger tilbage på ordren. */
    public void addParticipant(Participant participant) {
        participants.add(participant);
        participant.setCustomerOrder(this);
    }

    public Integer getId() {
        return id;
    }

    public String getContactName() {
        return contactName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getNote() {
        return note;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public int getTotalOre() {
        return totalOre;
    }

    public void setTotalOre(int totalOre) {
        this.totalOre = totalOre;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public List<Participant> getParticipants() {
        return participants;
    }
}
