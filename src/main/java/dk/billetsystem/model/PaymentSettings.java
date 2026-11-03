package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Betalingsoplysningerne, som kunden ser efter en bestilling. Svarer til tabellen "payment_settings",
 * som altid har præcis én række (id = 1). Arrangørerne retter den på adminsiden under "Betaling".
 */
@Entity
@Table(name = "payment_settings")
public class PaymentSettings {

    public static final int ID = 1;

    @Id
    private Integer id;

    private String accountHolder;
    private String regNumber;
    private String accountNumber;
    private String mobilepayNumber;
    private int paymentDays;
    private String instructions;
    private OffsetDateTime updatedAt;

    protected PaymentSettings() {
    }

    public void update(String accountHolder, String regNumber, String accountNumber,
                       String mobilepayNumber, int paymentDays, String instructions) {
        this.accountHolder = accountHolder;
        this.regNumber = regNumber;
        this.accountNumber = accountNumber;
        this.mobilepayNumber = mobilepayNumber;
        this.paymentDays = paymentDays;
        this.instructions = instructions;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Er der nok oplysninger til, at kunden kan betale? (Enten en bankkonto eller MobilePay.) */
    public boolean isConfigured() {
        return (regNumber != null && accountNumber != null) || mobilepayNumber != null;
    }

    public String getAccountHolder() {
        return accountHolder;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getMobilepayNumber() {
        return mobilepayNumber;
    }

    public int getPaymentDays() {
        return paymentDays;
    }

    public String getInstructions() {
        return instructions;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
