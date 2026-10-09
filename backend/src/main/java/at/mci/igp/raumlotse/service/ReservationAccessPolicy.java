package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.PermissionCode;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.ReservationResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Reservation access follows the current own and other reservation permissions. Ownership uses the stable user ID,
 * never the display name; a reservation without a recorded owner is treated as another user's reservation.
 */
@Component
public class ReservationAccessPolicy {

    private final EffectivePermissionService permissions;

    @Autowired
    public ReservationAccessPolicy(EffectivePermissionService permissions) {
        this.permissions = permissions;
    }

    /** Legacy constructor for isolated domain tests without a database-backed identity. */
    public ReservationAccessPolicy() {
        this.permissions = null;
    }

    public boolean canManage(Reservation reservation, Actor actor) {
        if (permissions == null) return actor.admin() || isOwner(reservation, actor);
        var current = permissions.snapshot(actor.userId());
        return current.has(isOwner(reservation, actor)
                ? PermissionCode.OWN_RESERVATION_MANAGE : PermissionCode.OTHER_RESERVATION_MANAGE);
    }

    /** Refuses like a missing reservation so the existence of other users' bookings is not revealed. */
    public void requireManage(Reservation reservation, Actor actor, String action) {
        boolean owner = isOwner(reservation, actor);
        boolean allowed = canManage(reservation, actor);
        if (!allowed) {
            AccessDeniedLog.ownership(action, actor.userId());
            if (owner) throw new at.mci.igp.raumlotse.exception.UserRoleException(403, "PERMISSION_REQUIRED",
                    "Sie dÃ¼rfen diese Aktion nicht ausfÃ¼hren.");
            throw reservationNotFound();
        }
    }

    /** Complete view for the owner or a user with the other-reservation right, occupancy otherwise. */
    public ReservationResponse view(Reservation reservation, Actor actor) {
        if (permissions == null ? canManage(reservation, actor)
                : isOwner(reservation, actor) || permissions.snapshot(actor.userId()).has(PermissionCode.OTHER_RESERVATION_MANAGE)) {
            return ReservationResponse.from(reservation, isOwner(reservation, actor));
        }
        return ReservationResponse.redacted(reservation);
    }

    public void requireRead(Reservation reservation, Actor actor) {
        if (permissions != null) permissions.require(actor.userId(), PermissionCode.READ);
        if (!isOwner(reservation, actor) && (permissions == null
                ? !actor.admin()
                : !permissions.snapshot(actor.userId()).has(PermissionCode.OTHER_RESERVATION_MANAGE))) {
            AccessDeniedLog.ownership("read", actor.userId());
            throw reservationNotFound();
        }
    }

    public ReservationResponse viewForSchedule(Reservation reservation, Actor actor) {
        return isOwner(reservation, actor) ? ReservationResponse.from(reservation, true)
                : ReservationResponse.redacted(reservation);
    }

    private static boolean isOwner(Reservation reservation, Actor actor) {
        return reservation.getCreatedByUserId() != null && reservation.getCreatedByUserId().equals(actor.userId());
    }

    private static NotFoundException reservationNotFound() {
        return new NotFoundException("Reservierung nicht gefunden.", "RESERVATION_NOT_FOUND");
    }
}
