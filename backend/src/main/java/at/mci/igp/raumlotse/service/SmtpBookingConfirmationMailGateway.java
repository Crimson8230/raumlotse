package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.config.BookingConfirmationProperties;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpBookingConfirmationMailGateway implements BookingConfirmationMailGateway {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final JavaMailSender mailSender;
    private final BookingConfirmationProperties properties;

    public SmtpBookingConfirmationMailGateway(JavaMailSender mailSender, BookingConfirmationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(Command command) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.sender());
            helper.setTo(command.recipient());
            helper.setSubject("Raumlotse: Buchung bestätigt – " + command.roomName());
            helper.setText(body(command), false);
            message.saveChanges();
            mailSender.send(message);
        } catch (MessagingException | IllegalArgumentException exception) {
            throw new MessageFormatException(exception);
        } catch (MailException exception) {
            throw new SubmissionException(exception);
        }
    }

    private String body(Command command) {
        return """
                Ihre Raumbuchung wurde bestätigt.

                Raum: %s
                Beginn: %s Europe/Berlin
                Ende: %s Europe/Berlin
                """.formatted(command.roomName(), format(command.startTime()), format(command.endTime()));
    }

    private String format(java.time.Instant instant) {
        return DATE_TIME.format(instant.atZone(properties.zone()));
    }
}
