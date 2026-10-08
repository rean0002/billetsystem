package dk.billetsystem.repository;

import dk.billetsystem.model.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Opslag af bestillinger. Indtil videre har vi kun brug for de færdige metoder,
 * især save(), som gemmer en ordre sammen med dens deltagere.
 */
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Integer> {
}
