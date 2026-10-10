package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.dto.PresenceEventResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

/** Simulated motion sensor (feature 014): presence is recorded for a booking in use, nothing else changes. */
@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-09T08:00:00Z");

    @Mock RoomRepository rooms;
    @Mock ReservationRepository reservations;

    private PresenceService service;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        lenient().when(rooms.findById(roomId)).thenReturn(Optional.of(new Room("R", new Floor(new Building("B"), "1"))));
        service = new PresenceService(rooms, reservations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void motionDuringABookingInUseRecordsTheLatestPresence() {
        Reservation active = new Reservation();
        active.setId(UUID.randomUUID());
        active.setStatus(ReservationStatus.ACTIVE);
        when(reservations.findActiveCovering(roomId, NOW)).thenReturn(Optional.of(active));

        Logger logger = (Logger) LoggerFactory.getLogger(PresenceService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        PresenceEventResponse result;
        try {
            result = service.recordMotion(roomId);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(result.recorded()).isTrue();
        assertThat(result.lastPresenceAt()).isEqualTo(NOW);
        assertThat(active.getLastPresenceAt()).isEqualTo(NOW);
        assertThat(active.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(reservations).save(active);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .contains("presence_event_recorded roomId=" + roomId + " reservationId=" + active.getId());
    }

    @Test
    void motionWithoutABookingInUseRecordsNothing() {
        when(reservations.findActiveCovering(roomId, NOW)).thenReturn(Optional.empty());

        PresenceEventResponse result = service.recordMotion(roomId);

        assertThat(result.recorded()).isFalse();
        assertThat(result.lastPresenceAt()).isNull();
        verify(reservations, never()).save(any());
    }

    @Test
    void unknownRoomIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(rooms.findById(unknown)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordMotion(unknown)).isInstanceOf(NotFoundException.class);
    }
}
