package at.mci.igp.raumlotse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** The single row of admin-editable check-in times (feature 014, FR-022). */
@Entity
@Table(name = "check_in_settings")
public class CheckInSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;
    @Column(name = "early_check_in_minutes", nullable = false)
    private int earlyCheckInMinutes;
    @Column(name = "grace_period_minutes", nullable = false)
    private int gracePeriodMinutes;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    protected CheckInSettings() { }

    public CheckInSettings(int earlyCheckInMinutes, int gracePeriodMinutes) {
        this.earlyCheckInMinutes = earlyCheckInMinutes;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.updatedAt = Instant.now();
    }

    public void change(int earlyCheckInMinutes, int gracePeriodMinutes, UUID adminUserId, Instant at) {
        this.earlyCheckInMinutes = earlyCheckInMinutes;
        this.gracePeriodMinutes = gracePeriodMinutes;
        this.updatedByUserId = adminUserId;
        this.updatedAt = at;
    }

    public int getEarlyCheckInMinutes() { return earlyCheckInMinutes; }
    public int getGracePeriodMinutes() { return gracePeriodMinutes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedByUserId() { return updatedByUserId; }
}
