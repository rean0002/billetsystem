package dk.billetsystem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Det, hjemmesiden sender, når nogen bestiller billetter.
 * Annotationerne (@NotBlank, @Email ...) er automatisk validering: er noget forkert,
 * svarer serveren med fejlkode 400, før vores egen kode overhovedet kører.
 *
 * Bemærk: der står ingen pris her. Prisen findes kun på serveren, så ingen kan snyde.
 */
public record OrderRequest(
        @NotBlank(message = "Kontaktpersonens navn mangler") @Size(max = 150) String contactName,
        @NotBlank(message = "E-mail mangler") @Email(message = "E-mailen ser ikke rigtig ud") @Size(max = 255) String email,
        @Size(max = 30) String phone,
        String note,
        @NotEmpty(message = "Der skal mindst være én deltager") @Size(max = 20, message = "Maks. 20 deltagere pr. bestilling")
        @Valid List<ParticipantRequest> participants
) {
    public record ParticipantRequest(
            @NotBlank(message = "Deltagerens fulde navn mangler") @Size(max = 150) String fullName,
            @NotNull(message = "Billettype mangler") Integer ticketTypeId
    ) {
    }
}
