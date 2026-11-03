package dk.billetsystem.dto;

import dk.billetsystem.model.Event;

import java.time.LocalDate;

/** Et event på adminsiden, med de vigtigste tal. */
public record AdminEventResponse(
        Integer id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        Integer maxParticipants,
        String publicInfo,
        long ticketsPaid,
        long ticketsPending,
        long revenuePaidOre,
        boolean isDefault
) {
    public static AdminEventResponse from(Event e, long paid, long pending, long revenue, boolean isDefault) {
        return new AdminEventResponse(e.getId(), e.getName(), e.getStartDate(), e.getEndDate(), e.getMaxParticipants(),
                e.getPublicInfo(), paid, pending, revenue, isDefault);
    }
}
