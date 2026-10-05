package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.config.BookingConfirmationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpBookingConfirmationMailGatewayTest {
    @Mock JavaMailSender sender;

    @Test
    void sendsExactGermanUtf8MessageToAccountRecipientAndExcludesUnrequestedData() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        var gateway = new SmtpBookingConfirmationMailGateway(sender, properties());

        gateway.send(new BookingConfirmationMailGateway.Command(
                "booker@example.test", "Besprechungsraum Ä", Instant.parse("2026-10-24T22:30:00Z"),
                Instant.parse("2026-10-25T02:30:00Z")));

        var captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("booker@example.test");
        assertThat(sent.getSubject()).isEqualTo("Raumlotse: Buchung bestätigt – Besprechungsraum Ä");
        assertThat(sent.getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(sent.getContent().toString())
                .contains("Raum: Besprechungsraum Ä", "Beginn: 25.10.2026 00:30 Europe/Berlin",
                        "Ende: 25.10.2026 03:30 Europe/Berlin")
                .doesNotContain("reservedFor", "attendees", "note");
    }

    private static BookingConfirmationProperties properties() {
        return new BookingConfirmationProperties("noreply@example.test", ZoneId.of("Europe/Berlin"),
                Duration.ofSeconds(1), 20, Duration.ofSeconds(30), Duration.ofSeconds(5),
                Duration.ofSeconds(5), Duration.ofSeconds(5));
    }
}
