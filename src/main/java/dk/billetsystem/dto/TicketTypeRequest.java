package dk.billetsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** Det, adminsiden sender, når en billettype oprettes eller ændres. */
public record TicketTypeRequest(
        @NotBlank(message = "Navn mangler") @Size(max = 100, message = "Maks. 100 tegn") String name,
        @NotNull(message = "Pris mangler") @PositiveOrZero(message = "Prisen kan ikke være negativ") Integer priceOre,
        @NotNull(message = "Startdato mangler") OffsetDateTime validFrom,
        OffsetDateTime validTo,
        boolean active
) {
}
