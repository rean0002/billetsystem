package dk.billetsystem.repository;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.OrderStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Bestillinger med en bestemt status, fx dem der afventer betaling. */
    List<CustomerOrder> findByStatus(OrderStatus status);

    /** Alle bestillinger med billetter til et event, uanset status. */
    @Query("select distinct o from CustomerOrder o join o.participants p where p.ticketType.event.id = :eventId")
    List<CustomerOrder> findByEventId(@Param("eventId") Integer eventId);

    /**
     * Sletter betalinger, der hører til bestillingerne. Tabellen payment har ingen Java-klasse endnu
     * (den bruges først, når Stripe kommer), så det gøres med almindelig SQL.
     */
    @Modifying
    @Query(value = "delete from payment where order_id in (:orderIds)", nativeQuery = true)
    void deletePaymentsForOrders(@Param("orderIds") List<Integer> orderIds);
}
