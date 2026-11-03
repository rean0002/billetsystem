package dk.billetsystem.dto;

import dk.billetsystem.model.PaymentSettings;

import java.time.OffsetDateTime;

public record PaymentSettingsResponse(
        String accountHolder,
        String regNumber,
        String accountNumber,
        String mobilepayNumber,
        int paymentDays,
        String instructions,
        boolean configured,
        OffsetDateTime updatedAt
) {
    public static PaymentSettingsResponse from(PaymentSettings s) {
        return new PaymentSettingsResponse(s.getAccountHolder(), s.getRegNumber(), s.getAccountNumber(),
                s.getMobilepayNumber(), s.getPaymentDays(), s.getInstructions(), s.isConfigured(), s.getUpdatedAt());
    }
}
