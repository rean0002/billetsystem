package dk.billetsystem.mail;

import dk.billetsystem.dto.PaymentInstructions;
import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.model.Event;
import dk.billetsystem.model.Participant;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Skriver indholdet af mailene: en almindelig tekst-udgave og en HTML-udgave med samme indhold.
 * (Nogle mailprogrammer viser kun teksten, så den skal kunne stå alene.)
 *
 * Alt, kunderne selv har skrevet (navne, beskeder), bliver "escaped" i HTML'en,
 * så det aldrig kan tolkes som HTML-kode.
 */
@Component
public class OrderMailBuilder {

    private static final Locale DANISH = Locale.of("da", "DK");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM yyyy", DANISH);
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("EEEE 'den' d. MMMM yyyy", DANISH);

    // Samme farver som hjemmesiden
    private static final String INK = "#24222b";
    private static final String MUTED = "#5f5a66";
    private static final String LINE = "#e2d9cb";
    private static final String PAPER = "#f6f1e9";

    public record Mail(String subject, String text, String html) {
    }

    /** Mailen lige efter bestilling: hvad er bestilt, og hvordan betaler man. */
    public Mail confirmation(CustomerOrder order, PaymentInstructions payment) {
        Event event = order.getEvent();
        Body body = new Body();
        body.paragraph("Hej " + order.getContactName());
        body.paragraph("Tak for din bestilling til " + event.getName() + " (" + dates(event) + ").");
        orderDetails(body, order);

        if (payment != null) {
            body.heading("Sådan betaler du");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Beløb", kroner(payment.amountOre())});
            if (payment.regNumber() != null && payment.accountNumber() != null) {
                rows.add(new String[]{"Reg.nr.", payment.regNumber()});
                rows.add(new String[]{"Kontonr.", payment.accountNumber()});
                if (payment.accountHolder() != null) rows.add(new String[]{"Modtager", payment.accountHolder()});
            }
            if (payment.mobilepayNumber() != null) rows.add(new String[]{"MobilePay", payment.mobilepayNumber()});
            rows.add(new String[]{"Skriv i beskeden", payment.reference()});
            rows.add(new String[]{"Betal senest", capitalize(payment.dueDate().format(DUE))});
            body.table(rows);
            if (payment.instructions() != null) body.paragraph(payment.instructions());
            body.paragraph("Du er tilmeldt, når vi har modtaget betalingen. Vi sender en mail, når den er registreret.");
        } else {
            body.paragraph("Du får besked om, hvordan du betaler.");
        }

        goodToKnow(body, event);
        body.muted("Har du spørgsmål, kan du svare på denne mail.");

        return body.build("Bekræftelse på bestilling nr. " + order.getId() + " – " + event.getName());
    }

    /** Mailen, når arrangørerne har registreret betalingen. */
    public Mail paymentReceived(CustomerOrder order) {
        Event event = order.getEvent();
        Body body = new Body();
        body.paragraph("Hej " + order.getContactName());
        body.paragraph("Vi har modtaget din betaling på " + kroner(order.getTotalOre()) + " for bestilling nr. " + order.getId()
                + ". Du er nu tilmeldt " + event.getName() + " (" + dates(event) + ").");
        orderDetails(body, order);
        goodToKnow(body, event);
        body.paragraph("Vi glæder os til at se dig.");
        body.muted("Har du spørgsmål, kan du svare på denne mail.");

        return body.build("Betaling modtaget – du er tilmeldt " + event.getName());
    }

    // -----------------------------------------------------------------

    private static void orderDetails(Body body, CustomerOrder order) {
        body.heading("Bestilling nr. " + order.getId());
        List<String[]> rows = new ArrayList<>();
        for (Participant p : order.getParticipants()) {
            String wishes = String.join(", ", wishes(p));
            rows.add(new String[]{p.getFullName() + (wishes.isEmpty() ? "" : " (" + wishes + ")"),
                    p.getTicketType().getName() + ", " + kroner(p.getPriceOre())});
        }
        rows.add(new String[]{"I alt", kroner(order.getTotalOre())});
        body.table(rows);
        if (!order.getSeatWithNames().isEmpty()) {
            body.paragraph("I vil gerne sidde sammen med: " + String.join(", ", order.getSeatWithNames())
                    + ". Vi prøver at opfylde alles ønsker.");
        }
    }

    private static List<String> wishes(Participant p) {
        List<String> wishes = new ArrayList<>();
        if (p.isBeginnerTable()) wishes.add("begynder-/singlebord");
        if (p.isNeedsTableEnd()) wishes.add("bordende");
        return wishes;
    }

    private static void goodToKnow(Body body, Event event) {
        if (event.getPublicInfo() == null) return;
        body.heading("Godt at vide");
        body.paragraph(event.getPublicInfo());
    }

    private static String dates(Event e) {
        LocalDate start = e.getStartDate();
        LocalDate end = e.getEndDate();
        return start.equals(end) ? start.format(DATE) : start.format(DATE) + " – " + end.format(DATE);
    }

    private static String kroner(int ore) {
        return NumberFormat.getCurrencyInstance(DANISH).format(ore / 100.0);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Bygger tekst- og HTML-udgaven samtidig, så de altid har samme indhold.
     */
    private static final class Body {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder html = new StringBuilder();

        void paragraph(String value) {
            text.append(value).append("\n\n");
            html.append("<p style=\"margin:0 0 14px\">").append(escape(value)).append("</p>");
        }

        void muted(String value) {
            text.append(value).append("\n");
            html.append("<p style=\"margin:20px 0 0;color:").append(MUTED).append(";font-size:14px\">")
                    .append(escape(value)).append("</p>");
        }

        void heading(String value) {
            text.append(value.toUpperCase(DANISH)).append("\n");
            html.append("<h2 style=\"margin:24px 0 8px;font-size:17px;color:").append(INK).append("\">")
                    .append(escape(value)).append("</h2>");
        }

        void table(List<String[]> rows) {
            int width = rows.stream().mapToInt(r -> r[0].length()).max().orElse(0);
            html.append("<table role=\"presentation\" style=\"width:100%;border-collapse:collapse;margin:0 0 14px\">");
            for (String[] row : rows) {
                text.append(String.format("%-" + (width + 2) + "s%s%n", row[0] + ":", row[1]));
                html.append("<tr><td style=\"padding:6px 12px 6px 0;border-bottom:1px solid ").append(LINE)
                        .append(";color:").append(MUTED).append("\">").append(escape(row[0])).append("</td>")
                        .append("<td style=\"padding:6px 0;border-bottom:1px solid ").append(LINE)
                        .append(";font-weight:600;text-align:right\">").append(escape(row[1])).append("</td></tr>");
            }
            text.append("\n");
            html.append("</table>");
        }

        Mail build(String subject) {
            String page = "<!DOCTYPE html><html lang=\"da\"><head><meta charset=\"UTF-8\"></head>"
                    + "<body style=\"margin:0;padding:24px 12px;background:" + PAPER + "\">"
                    + "<div style=\"max-width:560px;margin:0 auto;background:#fffdf9;border:1px solid " + LINE
                    + ";border-radius:10px;padding:28px;font-family:-apple-system,'Segoe UI',Arial,sans-serif;"
                    + "font-size:15px;line-height:1.55;color:" + INK + "\">"
                    + html
                    + "</div></body></html>";
            return new Mail(subject, text.toString().stripTrailing() + "\n", page);
        }

        /** Escaper tekst til HTML og bevarer linjeskift. */
        private static String escape(String value) {
            return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name()).replace("\n", "<br>");
        }
    }
}
