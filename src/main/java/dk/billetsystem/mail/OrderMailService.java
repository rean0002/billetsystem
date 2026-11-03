package dk.billetsystem.mail;

import dk.billetsystem.model.CustomerOrder;
import dk.billetsystem.repository.CustomerOrderRepository;
import dk.billetsystem.service.PaymentSettingsService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Sender bekræftelsesmails til kunderne.
 *
 * Er der ikke sat en mailserver op (MAIL_HOST), sendes intet, og bestillinger virker som før.
 * Mails i baggrunden (@Async) må aldrig få en bestilling til at fejle, så fejl bliver kun skrevet i loggen.
 */
@Service
public class OrderMailService {

    private static final Logger log = LoggerFactory.getLogger(OrderMailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final CustomerOrderRepository orderRepository;
    private final PaymentSettingsService paymentSettingsService;
    private final OrderMailBuilder builder;
    private final String host;
    private final String from;
    private final String replyTo;

    public OrderMailService(ObjectProvider<JavaMailSender> mailSender,
                            CustomerOrderRepository orderRepository,
                            PaymentSettingsService paymentSettingsService,
                            OrderMailBuilder builder,
                            @Value("${spring.mail.host:}") String host,
                            @Value("${billetsystem.mail.from}") String from,
                            @Value("${billetsystem.mail.reply-to:}") String replyTo) {
        this.mailSender = mailSender;
        this.orderRepository = orderRepository;
        this.paymentSettingsService = paymentSettingsService;
        this.builder = builder;
        this.host = host;
        this.from = from;
        this.replyTo = replyTo;
    }

    /** Er der sat en mailserver op? */
    public boolean isEnabled() {
        return !host.isBlank();
    }

    @Async
    @Transactional
    public void sendConfirmationInBackground(int orderId) {
        if (!isEnabled()) return; // ingen mailserver: bestillingen virker stadig, der sendes bare ingen mail
        try {
            sendConfirmation(orderId);
        } catch (RuntimeException e) {
            log.error("Kunne ikke sende bekræftelse på bestilling {}: {}", orderId, e.getMessage());
        }
    }

    @Async
    @Transactional
    public void sendPaymentReceivedInBackground(int orderId) {
        if (!isEnabled()) return; // ingen mailserver: bestillingen virker stadig, der sendes bare ingen mail
        try {
            sendPaymentReceived(orderId);
        } catch (RuntimeException e) {
            log.error("Kunne ikke sende mail om betaling for bestilling {}: {}", orderId, e.getMessage());
        }
    }

    /** Sender bekræftelsen nu og kaster en fejl, hvis det ikke lykkes. Bruges, når admin trykker "Send igen". */
    @Transactional
    public void sendConfirmation(int orderId) {
        if (!isEnabled()) throw new IllegalStateException("Der er ikke sat en mailserver op (MAIL_HOST)");
        CustomerOrder order = load(orderId);
        send(order, builder.confirmation(order, paymentSettingsService.instructionsFor(order)));
        order.markConfirmationSent();
        log.info("Bekræftelse sendt for bestilling {}", orderId);
    }

    @Transactional
    public void sendPaymentReceived(int orderId) {
        if (!isEnabled()) throw new IllegalStateException("Der er ikke sat en mailserver op (MAIL_HOST)");
        CustomerOrder order = load(orderId);
        send(order, builder.paymentReceived(order));
        order.markPaidMailSent();
        log.info("Mail om modtaget betaling sendt for bestilling {}", orderId);
    }

    private CustomerOrder load(int orderId) {
        return orderRepository.findWithParticipantsById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Bestilling " + orderId + " findes ikke"));
    }

    private void send(CustomerOrder order, OrderMailBuilder.Mail mail) {
        JavaMailSender sender = mailSender.getObject();
        try {
            MimeMessage message = sender.createMimeMessage();
            // true = mailen har både en almindelig tekst og en HTML-udgave
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(address(from));
            if (!replyTo.isBlank()) helper.setReplyTo(address(replyTo));
            helper.setTo(order.getEmail());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            throw new IllegalStateException("Mailen kunne ikke sendes: " + e.getMessage(), e);
        }
    }

    /** "Eventforeningen <billetter@example.com>" -> adresse med navnet kodet, så æøå vises rigtigt. */
    private static InternetAddress address(String value) throws MessagingException, UnsupportedEncodingException {
        InternetAddress address = new InternetAddress(value, true);
        if (address.getPersonal() != null) address.setPersonal(address.getPersonal(), StandardCharsets.UTF_8.name());
        return address;
    }
}
