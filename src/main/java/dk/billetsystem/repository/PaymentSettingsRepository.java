package dk.billetsystem.repository;

import dk.billetsystem.model.PaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettingsRepository extends JpaRepository<PaymentSettings, Integer> {
}
