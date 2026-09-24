package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.repository.EquipmentTypeRepository;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.SeatingArrangementRepository;
import at.mci.igp.raumlotse.service.ReservationService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class ConfigurationPrivacyTest {

    @Test
    void composeRequiresCredentialsInsteadOfCommittingDefaults() throws Exception {
        Path compose = Path.of(System.getProperty("user.dir")).getParent().resolve("docker-compose.yml");
        String yaml = Files.readString(compose);
        assertThat(yaml).contains("POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:")
                .contains("PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL:")
                .contains("PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD:")
                .doesNotContain("POSTGRES_PASSWORD: raumlotse", "PGADMIN_DEFAULT_PASSWORD: admin",
                        "PGADMIN_DEFAULT_EMAIL: admin@admin.com");
    }

    @Test
    void reservationSweepLoggingExcludesPersonalDataAndNotes() throws Exception {
        var reservationRepo = mock(ReservationRepository.class);
        var roomRepo = mock(RoomRepository.class);
        var seatingRepo = mock(SeatingArrangementRepository.class);
        var equipRepo = mock(EquipmentTypeRepository.class);
        Instant fixedNow = Instant.parse("2026-09-23T12:00:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);

        UUID resId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Room room = new Room("Room 101", new Floor(new Building("Main"), "1"));
        setField(room, "id", roomId);

        Reservation unattended = new Reservation();
        setField(unattended, "id", resId);
        setField(unattended, "room", room);
        setField(unattended, "startTime", fixedNow.minusSeconds(600));
        setField(unattended, "endTime", fixedNow.plusSeconds(1800));
        setField(unattended, "status", ReservationStatus.RESERVED);
        setField(unattended, "createdBy", "private-organizer@mci.edu");
        setField(unattended, "note", "Strictly confidential board meeting");

        when(reservationRepo.findUnattendedReservationsForExpiration(any(), any(), any()))
                .thenReturn(List.of(unattended));
        when(reservationRepo.findByStatusAndEndTimeLessThanEqual(any(), any()))
                .thenReturn(List.of());
        when(reservationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Logger logger = (Logger) LoggerFactory.getLogger(ReservationService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);

        try {
            var service = new ReservationService(reservationRepo, roomRepo, seatingRepo, equipRepo, clock);
            service.sweepOverdueReservations();

            var logs = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(logs).isNotEmpty();
            for (String message : logs) {
                assertThat(message)
                        .doesNotContain("private-organizer@mci.edu")
                        .doesNotContain("Strictly confidential board meeting")
                        .doesNotContain("confidential");
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
