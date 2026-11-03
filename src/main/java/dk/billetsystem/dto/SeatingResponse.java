package dk.billetsystem.dto;

import java.util.List;

/**
 * Overblik til bordplanen for ét event. Kun bestillinger, der afventer betaling eller er betalt, tæller med.
 *
 * groups: bestillinger med flere deltagere, eller hvor gruppen vil sidde med andre.
 * beginnerTable: deltagere, der ønsker begynder-/singlebordet.
 * tableEnd: deltagere, der har brug for en bordende af helbredsmæssige årsager.
 */
public record SeatingResponse(
        List<Group> groups,
        List<Person> beginnerTable,
        List<Person> tableEnd,
        long seatsTaken
) {
    /** En bestilling: deltagerne sidder sammen, og evt. med de navne, de har skrevet. */
    public record Group(Integer orderId, String contactName, String status, List<String> participants, List<Wish> seatWith) {
    }

    /**
     * Et navn, gruppen vil sidde med. foundInOrderId er bestillingen, hvor navnet findes som deltager,
     * eller null, hvis personen ikke har en billet (endnu), eller navnet er stavet anderledes.
     */
    public record Wish(String name, Integer foundInOrderId) {
    }

    public record Person(String fullName, Integer orderId, String status, String contactName) {
    }
}
