package dk.billetsystem.dto;

import java.time.LocalDate;

/**
 * Det, kunden skal bruge for at betale en bestilling: beløb, hvor pengene skal hen,
 * en reference, så arrangørerne kan se, hvilken bestilling betalingen hører til, og en frist.
 */
public record PaymentInstructions(
        int amountOre,
        String reference,
        LocalDate dueDate,
        String accountHolder,
        String regNumber,
        String accountNumber,
        String mobilepayNumber,
        String instructions
) {
}
