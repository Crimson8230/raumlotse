package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.config.ReservationPolicyConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReservationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationService reservationService;

    public ReservationExpirationScheduler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(
            initialDelay = ReservationPolicyConstants.EXPIRATION_POLL_INTERVAL_MS,
            fixedDelay = ReservationPolicyConstants.EXPIRATION_POLL_INTERVAL_MS
    )
    public void runExpirationSweep() {
        try {
            var sweep = reservationService.sweepOverdueReservations();
            if (sweep.expiredCount() > 0 || sweep.completedCount() > 0) {
                log.info("Scheduled reservation sweep completed: expired_count={} completed_count={}",
                        sweep.expiredCount(), sweep.completedCount());
            }
        } catch (Exception e) {
            log.error("Scheduled reservation sweep encountered an error", e);
        }
    }
}
