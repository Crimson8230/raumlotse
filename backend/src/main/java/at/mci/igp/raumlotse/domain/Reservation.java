package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seating_arrangement_id", nullable = false)
    private SeatingArrangement seatingArrangement;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.RESERVED;

    @Column(name = "expected_attendees", nullable = false)
    private int expectedAttendees;

    @Column(name = "note")
    private String note;

    @Column(name = "reserved_for", nullable = false)
    private String reservedFor;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_in_method")
    private CheckInMethod checkInMethod;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by_user_id")
    private UUID checkedInByUserId;

    @Column(name = "last_presence_at")
    private Instant lastPresenceAt;

    @Version
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reservation_equipment",
            joinColumns = @JoinColumn(name = "reservation_id"),
            inverseJoinColumns = @JoinColumn(name = "equipment_type_id"))
    private List<EquipmentType> additionalEquipment = new ArrayList<>();

    public Reservation() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public SeatingArrangement getSeatingArrangement() {
        return seatingArrangement;
    }

    public void setSeatingArrangement(SeatingArrangement seatingArrangement) {
        this.seatingArrangement = seatingArrangement;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public int getExpectedAttendees() {
        return expectedAttendees;
    }

    public void setExpectedAttendees(int expectedAttendees) {
        this.expectedAttendees = expectedAttendees;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getReservedFor() {
        return reservedFor;
    }

    public void setReservedFor(String reservedFor) {
        this.reservedFor = reservedFor;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(UUID createdByUserId) {
        if (this.createdByUserId != null && !this.createdByUserId.equals(createdByUserId)) {
            throw new IllegalStateException("Reservation ownership is immutable.");
        }
        this.createdByUserId = createdByUserId;
    }

    public CheckInMethod getCheckInMethod() { return checkInMethod; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public UUID getCheckedInByUserId() { return checkedInByUserId; }

    /** Records how, by whom and when the reservation was checked in; the three values are always set together. */
    public void recordCheckIn(CheckInMethod method, UUID actorUserId, Instant at) {
        this.checkInMethod = method;
        this.checkedInByUserId = actorUserId;
        this.checkedInAt = at;
    }

    public Instant getLastPresenceAt() { return lastPresenceAt; }
    public void setLastPresenceAt(Instant lastPresenceAt) { this.lastPresenceAt = lastPresenceAt; }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<EquipmentType> getAdditionalEquipment() {
        return additionalEquipment;
    }

    public void setAdditionalEquipment(List<EquipmentType> additionalEquipment) {
        this.additionalEquipment = additionalEquipment != null ? additionalEquipment : new ArrayList<>();
    }
}
