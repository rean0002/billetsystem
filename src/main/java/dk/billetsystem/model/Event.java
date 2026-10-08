package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * En messe, fx "Hobbymesse april 2027".
 * Klassen svarer til tabellen "event" i databasen: én række i tabellen = ét Event-objekt.
 *
 * Spring/Hibernate oversætter automatisk Java-navne til databasenavne,
 * fx startDate -> start_date og maxParticipants -> max_participants.
 */
@Entity
@Table(name = "event")
public class Event {

    // SERIAL i databasen = databasen finder selv på næste id
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer maxParticipants; // null = ingen grænse

    // Hibernate skal bruge en tom konstruktør for at kunne oprette objekter fra databasen
    protected Event() {
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Integer getMaxParticipants() {
        return maxParticipants;
    }
}
