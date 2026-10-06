package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import org.springframework.stereotype.Component;

/**
 * Who may see and change a reservation (feature 013, FR-019 – FR-025): its owner (by user identity, never by
 * display name) and administrators. A reservation without a recorded owner can only be managed by administrators.
 */
@Component
public class ReservationAccessPolicy {

    public boolean canManage(Reservation reservation, Actor actor) {
        return actor.admin() || isOwner(reservation, actor);
    }

    /** Refuses like a missing reservation so the existence of other users' bookings is not revealed. */
    public void requireManage(Reservation reservation, Actor actor, String action) {
        if (!canManage(reservation, actor)) {
            AccessDeniedLog.ownership(action, actor.userId());
            throw new NotFoundException("Reservierung " + reservation.getId() + " nicht gefunden.");
        }
    }

    /** Complete view for owner and administrators, occupancy only for everybody else. */
    public ReservationResponse view(Reservation reservation, Actor actor) {
        if (canManage(reservation, actor)) {
            return ReservationResponse.from(reservation, isOwner(reservation, actor));
        }
        return ReservationResponse.redacted(reservation);
    }

    private static boolean isOwner(Reservation reservation, Actor actor) {
        return reservation.getCreatedByUserId() != null && reservation.getCreatedByUserId().equals(actor.userId());
    }
}
