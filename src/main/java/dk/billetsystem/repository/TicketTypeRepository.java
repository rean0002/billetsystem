package dk.billetsystem.repository;

import dk.billetsystem.model.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Et repository er stedet, hvor vi slår ting op i databasen.
 * Ved at "extends JpaRepository" får vi gratis metoder som findAll(), findById() og save().
 * Vi skal ikke selv skrive nogen implementering, det gør Spring.
 */
public interface TicketTypeRepository extends JpaRepository<TicketType, Integer> {

    /**
     * Billettyper, der kan købes på tidspunktet "now":
     * slået til af admin, startet, og enten uden slutdato eller ikke endnu udløbet.
     * Billigste først.
     */
    @Query("""
            select t from TicketType t
            where t.active = true
              and t.validFrom <= :now
              and (t.validTo is null or t.validTo >= :now)
            order by t.priceOre
            """)
    List<TicketType> findCurrent(@Param("now") OffsetDateTime now);
}
