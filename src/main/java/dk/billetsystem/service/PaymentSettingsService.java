package dk.billetsystem.service;

import dk.billetsystem.dto.PaymentInstructions;
import dk.billetsystem.dto.PaymentSettingsRequest;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.PaymentSettings;
import dk.billetsystem.repository.PaymentSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Betalingsoplysninger til bankoverførsel/MobilePay, og hvornår en bestilling skal være betalt.
 */
@Service
public class PaymentSettingsService {

    private final PaymentSettingsRepository repository;

    public PaymentSettingsService(PaymentSettingsRepository repository) {
        this.repository = repository;
    }

    /** Rækken oprettes af schema.sql (eller migrationen), så den findes altid. */
    @Transactional(readOnly = true)
    public PaymentSettings get() {
        return repository.findById(PaymentSettings.ID).orElseThrow(() -> new IllegalStateException(
                "Tabellen payment_settings mangler sin række. Kør database/migrations/2026-11-03-payment-settings.sql"));
    }

    @Transactional
    public PaymentSettings update(PaymentSettingsRequest r) {
        PaymentSettings settings = get();
        settings.update(blankToNull(r.accountHolder()), blankToNull(r.regNumber()), blankToNull(r.accountNumber()),
                blankToNull(r.mobilepayNumber()), r.paymentDays(), blankToNull(r.instructions()));
        return repository.save(settings);
    }

    /** Betalingsinfo til kunden, eller null hvis arrangørerne ikke har udfyldt den endnu. */
    public PaymentInstructions instructionsFor(CustomerOrder order) {
        PaymentSettings s = get();
        if (!s.isConfigured()) return null;
        return new PaymentInstructions(order.getTotalOre(), reference(order), dueDate(order, s),
                s.getAccountHolder(), s.getRegNumber(), s.getAccountNumber(), s.getMobilepayNumber(), s.getInstructions());
    }

    /** Teksten, kunden skriver i "besked til modtager", så betalingen kan matches med bestillingen. */
    public static String reference(CustomerOrder order) {
        return "Bestilling " + order.getId();
    }

    /** Sidste dag for betaling: bestillingsdatoen (dansk tid) plus antal dage fra indstillingerne. */
    public static LocalDate dueDate(CustomerOrder order, PaymentSettings settings) {
        return order.getCreatedAt().atZoneSameInstant(StatisticsService.DANISH_TIME).toLocalDate()
                .plusDays(settings.getPaymentDays());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
