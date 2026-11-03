package dk.billetsystem.repository;

import dk.billetsystem.model.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Integer> {

    /** Alle events, nyeste først. Til adminsiden. */
    List<Event> findAllByOrderByStartDateDesc();

    /** Det næste event, der ikke er slut endnu (eller er i gang i dag). */
    Optional<Event> findFirstByEndDateGreaterThanEqualOrderByStartDate(LocalDate today);

    /** Det seneste event. Bruges, hvis alle events er overstået. */
    Optional<Event> findFirstByOrderByStartDateDesc();

    /**
     * Henter eventet og "låser" rækken, til transaktionen er færdig (SELECT ... FOR UPDATE).
     * Bestiller to personer de sidste pladser samtidig, må den ene vente, til den anden er færdig.
     * Så kan eventet ikke blive overbooket.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Integer id);
}
