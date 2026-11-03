package dk.billetsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Det, adminsiden sender, når et event oprettes eller ændres. */
public record EventRequest(
        @NotBlank(message = "Navn mangler") @Size(max = 100, message = "Maks. 100 tegn") String name,
        @NotNull(message = "Startdato mangler") LocalDate startDate,
        @NotNull(message = "Slutdato mangler") LocalDate endDate,
        @Positive(message = "Skal være mindst 1, eller tom for ingen grænse") Integer maxParticipants,
        @Size(max = 2000, message = "Maks. 2000 tegn") String publicInfo
) {
}
