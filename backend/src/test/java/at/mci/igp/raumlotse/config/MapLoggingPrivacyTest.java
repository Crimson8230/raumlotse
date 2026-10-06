package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.exception.GlobalExceptionHandler;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.service.MapImageValidator;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/** Rejected uploads and map rule violations are logged as greppable codes, never with file content. */
class MapLoggingPrivacyTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void attach() {
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
    }

    List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void rejectedUploadLogsOnlyTheRejectionCode() {
        byte[] secret = "SECRET-IMAGE-CONTENT user@example.test".getBytes();
        try {
            new MapImageValidator().validate(secret);
        } catch (MapRequestException expected) {
            // expected
        }
        assertThat(messages()).anyMatch(m -> m.contains("map_image_rejected code=MAP_IMAGE_UNSUPPORTED"));
        assertThat(messages()).noneMatch(m -> m.contains("SECRET-IMAGE-CONTENT") || m.contains("user@example.test"));
    }

    @Test
    void oversizedUploadLogsTheCodeWithoutBytes() {
        try {
            new MapImageValidator().validate(new byte[(int) MapImageValidator.MAX_BYTES + 1]);
        } catch (MapRequestException expected) {
            // expected
        }
        assertThat(messages()).anyMatch(m -> m.contains("map_image_rejected code=MAP_IMAGE_TOO_LARGE"));
    }

    @Test
    void mapRequestRejectionsAreLoggedWithCodeAndStatusOnly() {
        new GlobalExceptionHandler().handleMapRequest(new MapRequestException(
                HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_FLOOR_MISMATCH", "Room 'Seminarraum 1' is not on the floor."));
        assertThat(messages()).anyMatch(m -> m.contains("map_request_rejected code=ROOM_FLOOR_MISMATCH status=422"));
        assertThat(messages()).noneMatch(m -> m.contains("Seminarraum"));
    }
}
