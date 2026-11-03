package dk.billetsystem.repository;

import dk.billetsystem.model.CustomerOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Opslag af bestillinger. save() bruges, når en ordre gemmes sammen med dens deltagere.
 *
 * @EntityGraph henter deltagerne og deres billettype i samme SQL-forespørgsel.
 * Ellers ville Hibernate lave én ekstra forespørgsel pr. bestilling (det kaldes "N+1-problemet").
 */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Integer> {

    /** Alle bestillinger, nyeste først. Til adminsiden. */
    @EntityGraph(attributePaths = {"participants", "participants.ticketType"})
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"participants", "participants.ticketType"})
    Optional<CustomerOrder> findWithParticipantsById(Integer id);
}
