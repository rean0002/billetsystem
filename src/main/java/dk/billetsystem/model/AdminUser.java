package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * En bruger, der kan logge ind på adminsiden. Svarer til tabellen "admin_user".
 *
 * Kodeordet gemmes aldrig i klartekst, kun som en BCrypt-hash. En hash kan ikke
 * regnes tilbage til kodeordet, så selv hvis databasen bliver lækket, er kodeordene beskyttet.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String username;
    private String passwordHash;
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected AdminUser() {
    }

    public AdminUser(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public Integer getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
