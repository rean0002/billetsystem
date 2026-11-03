package dk.billetsystem.dto;

import dk.billetsystem.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

/** Admin ændrer status på en bestilling, fx til PAID, når bankoverførslen er modtaget. */
public record StatusChangeRequest(@NotNull(message = "Status mangler") OrderStatus status) {
}
