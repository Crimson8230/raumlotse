package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.ReservationSweepResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationSchedulerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationExpirationScheduler scheduler;

    @Test
    void runExpirationSweep_delegatesToReservationServiceSweep() {
        when(reservationService.sweepOverdueReservations()).thenReturn(new ReservationSweepResponse(3, 2));

        assertThatCode(() -> scheduler.runExpirationSweep()).doesNotThrowAnyException();

        verify(reservationService).sweepOverdueReservations();
    }

    @Test
    void runExpirationSweep_zeroCounts_completesWithoutError() {
        when(reservationService.sweepOverdueReservations()).thenReturn(new ReservationSweepResponse(0, 0));

        assertThatCode(() -> scheduler.runExpirationSweep()).doesNotThrowAnyException();

        verify(reservationService).sweepOverdueReservations();
    }

    @Test
    void runExpirationSweep_serviceThrowsException_catchesCleanlyWithoutRethrowing() {
        doThrow(new RuntimeException("Database connectivity issue")).when(reservationService).sweepOverdueReservations();

        assertThatCode(() -> scheduler.runExpirationSweep()).doesNotThrowAnyException();

        verify(reservationService).sweepOverdueReservations();
    }
}
