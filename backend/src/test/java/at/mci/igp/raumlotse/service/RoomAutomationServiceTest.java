package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomDeviceKind;
import at.mci.igp.raumlotse.exception.DeviceOperationException;
import at.mci.igp.raumlotse.repository.ReservationRepository;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class RoomAutomationServiceTest {
    @Mock RoomDeviceService devices;
    @Mock ReservationRepository reservations;

    private RoomAutomationService automation;
    private Reservation reservation;
    private UUID roomId;

    @BeforeEach
    void setUp() throws Exception {
        automation = new RoomAutomationService(devices, reservations);
        roomId = UUID.randomUUID();
        Room room = new Room("Seminarraum 1", new Floor(new Building("Main"), "1"));
        Field id = Room.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(room, roomId);
        reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRoom(room);
    }

    @Test
    void prepareSwitchesLightAndVentilationOnAndUnlocksTheDoor() {
        RoomAutomationService.AutomationResult result = automation.prepare(reservation);

        verify(devices).apply(roomId, RoomDeviceKind.LIGHTING, true);
        verify(devices).apply(roomId, RoomDeviceKind.VENTILATION, true);
        verify(devices).apply(roomId, RoomDeviceKind.DOOR, true);
        verify(devices, never()).apply(eq(roomId), eq(RoomDeviceKind.PROJECTOR), anyBoolean());
        assertThat(result.failedDevices()).isEmpty();
    }

    @Test
    void aFailingDoorDoesNotStopTheOtherDevicesAndIsLoggedWithRoomBookingAndDevice() {
        lenient().doThrow(new DeviceOperationException("offline")).when(devices).apply(roomId, RoomDeviceKind.DOOR, true);

        String log = captureLog(() -> {
            RoomAutomationService.AutomationResult result = automation.prepare(reservation);
            assertThat(result.failedDevices()).containsExactly(RoomDeviceKind.DOOR);
            return result;
        });

        verify(devices).apply(roomId, RoomDeviceKind.LIGHTING, true);
        verify(devices).apply(roomId, RoomDeviceKind.VENTILATION, true);
        assertThat(log).contains("room_automation_device_failed", "roomId=" + roomId,
                "reservationId=" + reservation.getId(), "kind=DOOR", "phase=prepare");
    }

    @Test
    void prepareToleratesEveryDeviceFailing() {
        doThrow(new DeviceOperationException("offline")).when(devices).apply(eq(roomId), any(), eq(true));

        assertThat(automation.prepare(reservation).failedDevices())
                .containsExactly(RoomDeviceKind.LIGHTING, RoomDeviceKind.VENTILATION, RoomDeviceKind.DOOR);
    }

    @Test
    void releaseSwitchesLightAndVentilationOffAndLeavesDoorAndProjectorAlone() {
        RoomAutomationService.AutomationResult result = automation.release(reservation);

        verify(devices).apply(roomId, RoomDeviceKind.LIGHTING, false);
        verify(devices).apply(roomId, RoomDeviceKind.VENTILATION, false);
        verify(devices, never()).apply(eq(roomId), eq(RoomDeviceKind.DOOR), anyBoolean());
        verify(devices, never()).apply(eq(roomId), eq(RoomDeviceKind.PROJECTOR), anyBoolean());
        assertThat(result.failedDevices()).isEmpty();
    }

    @Test
    void releaseKeepsTheRoomRunningWhileAnotherBookingIsInUse() {
        when(reservations.existsByRoomIdAndStatusAndIdNot(roomId, ReservationStatus.ACTIVE, reservation.getId()))
                .thenReturn(true);

        assertThat(automation.release(reservation).failedDevices()).isEmpty();
        verifyNoInteractions(devices);
    }

    @Test
    void aFailingLightOnReleaseIsLoggedAndVentilationIsStillSwitchedOff() {
        lenient().doThrow(new DeviceOperationException("offline")).when(devices).apply(roomId, RoomDeviceKind.LIGHTING, false);

        String log = captureLog(() -> {
            assertThat(automation.release(reservation).failedDevices()).containsExactly(RoomDeviceKind.LIGHTING);
            return null;
        });

        verify(devices).apply(roomId, RoomDeviceKind.VENTILATION, false);
        assertThat(log).contains("room_automation_device_failed", "roomId=" + roomId,
                "reservationId=" + reservation.getId(), "kind=LIGHTING", "phase=release");
    }

    static String captureLog(Supplier<?> action) {
        Logger logger = (Logger) LoggerFactory.getLogger(RoomAutomationService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        try {
            action.get();
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", String::concat);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
