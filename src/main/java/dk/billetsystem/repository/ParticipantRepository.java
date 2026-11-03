package dk.billetsystem.repository;

import dk.billetsystem.model.OrderStatus;
import dk.billetsystem.model.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    /** Antal billetter til et event i bestillinger med de givne statusser (typisk PAID og PENDING). */
    @Query("""
            select count(p) from Participant p
            where p.ticketType.event.id = :eventId
              and p.customerOrder.status in :statuses
            """)
    long countByEventAndStatus(@Param("eventId") Integer eventId, @Param("statuses") Collection<OrderStatus> statuses);
}
