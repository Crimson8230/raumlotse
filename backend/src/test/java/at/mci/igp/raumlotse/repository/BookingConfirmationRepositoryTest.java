package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class BookingConfirmationRepositoryTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired BookingConfirmationRepository confirmations;

    private UUID reservationId;

    @BeforeEach
    void createReservationFixture() {
        jdbc.update("delete from booking_confirmation");
        UUID building = UUID.randomUUID();
        UUID floor = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        UUID seating = UUID.randomUUID();
        reservationId = UUID.randomUUID();
        jdbc.update("insert into building(id,name) values (?,?)", building, "Queue " + building);
        jdbc.update("insert into floor(id,building_id,name) values (?,?,?)", floor, building, "Ground");
        jdbc.update("insert into room(id,name,floor_id,not_barrier_free) values (?,?,?,false)", room, "A", floor);
        jdbc.update("insert into seating_arrangement(id,room_id,name,max_capacity) values (?,?,?,?)",
                seating, room, "Board", 4);
        jdbc.update("insert into reservation(id,room_id,seating_arrangement_id,start_time,end_time,status,expected_attendees,created_by,reserved_for) values (?,?,?,?,?,'RESERVED',1,'owner','Owner')",
                reservationId, room, seating, Timestamp.from(Instant.now().plusSeconds(3600)),
                Timestamp.from(Instant.now().plusSeconds(7200)));
    }

    @Test
    void schemaEnforcesIdentityUniquenessStateAndPrivacyColumns() {
        jdbc.update("insert into booking_confirmation(reservation_id) values (?)", reservationId);
        assertThatThrownBy(() -> jdbc.update("insert into booking_confirmation(reservation_id) values (?)", reservationId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into booking_confirmation(reservation_id,status) values (?, 'UNKNOWN')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update booking_confirmation set attempt_count=2 where reservation_id=?", reservationId))
                .isInstanceOf(DataIntegrityViolationException.class);

        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name='booking_confirmation'",
                String.class);
        assertThat(columns).doesNotContain("email_notification", "recipient_email", "subject", "body");
    }

    @Test
    void databaseRejectsInconsistentStateFields() {
        jdbc.update("insert into booking_confirmation(reservation_id) values (?)", reservationId);
        assertThatThrownBy(() -> jdbc.update(
                "update booking_confirmation set status='SENT', attempt_count=1 where reservation_id=?",
                reservationId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update booking_confirmation set status='FAILED', failed_at=now(), failure_code='NOT_ALLOWED' where reservation_id=?",
                reservationId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void skipLockedClaimDoesNotReturnAnAlreadyLockedPendingRow() throws Exception {
        jdbc.update("insert into booking_confirmation(reservation_id) values (?)", reservationId);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var holder = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                confirmations.findOldestPendingForUpdate(1);
                locked.countDown();
                try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
            assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var secondClaim = new TransactionTemplate(transactions).execute(status ->
                    confirmations.findOldestPendingForUpdate(1));
            assertThat(secondClaim).isEmpty();
            release.countDown();
            holder.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
