package dk.billetsystem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Det, adminsiden sender, når betalingsoplysningerne gemmes. Alle felter undtagen fristen er valgfrie,
 * men kunden kan først se betalingsinfo, når der er en bankkonto (reg.nr. + kontonr.) eller MobilePay.
 */
public record PaymentSettingsRequest(
        @Size(max = 100, message = "Maks. 100 tegn") String accountHolder,
        @Pattern(regexp = "\\d{4}", message = "Reg.nr. er 4 cifre") String regNumber,
        @Pattern(regexp = "\\d{1,10}", message = "Kontonummeret er op til 10 cifre") String accountNumber,
        @Size(max = 30, message = "Maks. 30 tegn") String mobilepayNumber,
        @Min(value = 1, message = "Mindst 1 dag") @Max(value = 60, message = "Højst 60 dage") int paymentDays,
        @Size(max = 1000, message = "Maks. 1000 tegn") String instructions
) {
}
