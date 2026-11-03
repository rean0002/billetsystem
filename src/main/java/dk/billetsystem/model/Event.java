package dk.billetsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Et event, fx "Forårsmessen 2027".
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
    private String publicInfo;       // praktisk info på bestillingssiden, fx gæstebilletter og åbningstider

    // Hibernate skal bruge en tom konstruktør for at kunne oprette objekter fra databasen
    protected Event() {
    }

    public Event(String name, LocalDate startDate, LocalDate endDate, Integer maxParticipants, String publicInfo) {
        update(name, startDate, endDate, maxParticipants, publicInfo);
    }

    public void update(String name, LocalDate startDate, LocalDate endDate, Integer maxParticipants, String publicInfo) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.maxParticipants = maxParticipants;
        this.publicInfo = publicInfo;
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

    public String getPublicInfo() {
        return publicInfo;
    }
}
