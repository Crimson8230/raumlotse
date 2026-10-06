package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Reservation;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.exception.NotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Feature 013, US4: ownership is decided by user identity, never by display name (FR-019 – FR-025). */
class ReservationAccessPolicyTest {

    private final ReservationAccessPolicy policy = new ReservationAccessPolicy();
    private final Actor owner = new Actor(UUID.randomUUID(), false);
    private final Actor other = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private Reservation owned;
    private Reservation legacy;

    @BeforeEach
    void reservations() throws Exception {
        owned = reservation(owner.userId(), "Same Name");
        legacy = reservation(null, "Same Name");
    }

    @Test
    void ownerAndAdministratorMayManage() {
        assertThat(policy.canManage(owned, owner)).isTrue();
        assertThat(policy.canManage(owned, admin)).isTrue();
    }

    @Test
    void otherUserWithTheSameDisplayNameMayNot() {
        assertThat(policy.canManage(owned, other)).isFalse();
    }

    @Test
    void reservationWithoutOwnerIdentityIsManageableByAdministratorsOnly() {
        assertThat(policy.canManage(legacy, owner)).isFalse();
        assertThat(policy.canManage(legacy, other)).isFalse();
        assertThat(policy.canManage(legacy, admin)).isTrue();
    }

    @Test
    void requireManageHidesExistenceFromOthers() {
        assertThatThrownBy(() -> policy.requireManage(owned, other, "cancel"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Reservierung " + owned.getId() + " nicht gefunden.");
        policy.requireManage(owned, owner, "cancel");
        policy.requireManage(owned, admin, "cancel");
    }

    @Test
    void ownerSeesEverythingOthersOnlyTheSlot() {
        var forOwner = policy.view(owned, owner);
        assertThat(forOwner.ownedByMe()).isTrue();
        assertThat(forOwner.note()).isEqualTo("private note");
        assertThat(forOwner.reservedFor()).isEqualTo("Jane");

        var forOther = policy.view(owned, other);
        assertThat(forOther.ownedByMe()).isFalse();
        assertThat(forOther.note()).isNull();
        assertThat(forOther.reservedFor()).isNull();
        assertThat(forOther.createdBy()).isNull();
        assertThat(forOther.additionalEquipment()).isEmpty();
        assertThat(forOther.id()).isEqualTo(owned.getId());
        assertThat(forOther.startTime()).isEqualTo(owned.getStartTime());
        assertThat(forOther.endTime()).isEqualTo(owned.getEndTime());
        assertThat(forOther.status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(forOther.roomName()).isEqualTo("Room 1");
    }

    @Test
    void administratorSeesDetailsButIsNotMarkedAsOwner() {
        var forAdmin = policy.view(owned, admin);
        assertThat(forAdmin.ownedByMe()).isFalse();
        assertThat(forAdmin.note()).isEqualTo("private note");
    }

    private static Reservation reservation(UUID ownerId, String createdBy) throws Exception {
        var room = new Room("Room 1", new Floor(new Building("Main"), "1"));
        var arrangement = new SeatingArrangement("Theater", 40);
        room.replaceSeatingArrangements(List.of(arrangement));
        var r = new Reservation();
        set(r, "id", UUID.randomUUID());
        set(r, "createdAt", Instant.parse("2026-10-01T10:00:00Z"));
        r.setRoom(room);
        r.setSeatingArrangement(arrangement);
        r.setStartTime(Instant.parse("2026-10-10T10:00:00Z"));
        r.setEndTime(Instant.parse("2026-10-10T11:00:00Z"));
        r.setStatus(ReservationStatus.RESERVED);
        r.setExpectedAttendees(10);
        r.setNote("private note");
        r.setCreatedBy(createdBy);
        r.setCreatedByUserId(ownerId);
        r.setReservedFor("Jane");
        return r;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }
}
