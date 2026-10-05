package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.repository.BookingConfirmationRepository;
import at.mci.igp.raumlotse.repository.BuildingRepository;
import at.mci.igp.raumlotse.repository.FloorRepository;
import at.mci.igp.raumlotse.repository.RoomRepository;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.BookingConfirmationQueueService;
import at.mci.igp.raumlotse.service.BookingConfirmationWorker;
import at.mci.igp.raumlotse.service.BookingConfirmationMailGateway;
import at.mci.igp.raumlotse.domain.BookingConfirmationStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;

class BookingConfirmationIntegrationTest extends AbstractIntegrationTest {
    @Autowired ReservationService reservations;
    @Autowired BookingConfirmationRepository confirmations;
    @Autowired BuildingRepository buildings;
    @Autowired FloorRepository floors;
    @Autowired RoomRepository rooms;
    @Autowired UserAccountRepository users;
    @Autowired BookingConfirmationQueueService queue;
    @MockitoBean BookingConfirmationMailGateway gateway;
    @Autowired JdbcTemplate jdbc;

    private Room room;
    private SeatingArrangement seating;
    private AuthenticatedUser actor;

    @AfterEach
    void removeMailFeatureFixtures() {
        jdbc.update("delete from booking_confirmation where reservation_id in (select id from reservation where created_by_user_id=?)",
                actor.userId());
        jdbc.update("delete from reservation where created_by_user_id=?", actor.userId());
        users.deleteById(actor.userId());
    }

    @BeforeEach
    void setUpFixture() {
        var account = users.save(new UserAccount(
                "mail-" + java.util.UUID.randomUUID() + "@example.test", "Booker",
                "{pbkdf2-sha256-600000-v1}" + "x".repeat(40)));
        actor = new AuthenticatedUser(account.getId(), account.getDisplayName());
        var building = buildings.save(new Building("Mail " + java.util.UUID.randomUUID()));
        var floor = floors.save(new Floor(building, "Ground"));
        room = new Room("Besprechung", floor);
        seating = new SeatingArrangement("Board", 8);
        room.replaceSeatingArrangements(List.of(seating));
        room = rooms.saveAndFlush(room);
        seating = room.getSeatingArrangements().getFirst();
    }

    @Test
    void commitsOneConfirmationOnlyForSuccessfulExplicitOptIn() {
        long before = confirmations.count();
        Instant start = Instant.now().plusSeconds(7200);
        var optedIn = request(start, true);
        var optedOut = request(start.plusSeconds(7200), false);

        var first = reservations.createReservation(room.getId(), optedIn, actor);
        var second = reservations.createReservation(room.getId(), optedOut, actor);

        assertThat(confirmations.findByReservationId(first.id())).isPresent();
        assertThat(confirmations.findByReservationId(second.id())).isEmpty();
        assertThat(confirmations.count()).isEqualTo(before + 1);
        reservations.getReservation(first.id());
        assertThat(confirmations.count()).isEqualTo(before + 1);
    }

    @Test
    void rejectedOptInDoesNotCommitAnotherConfirmation() {
        Instant start = Instant.now().plusSeconds(7200);
        reservations.createReservation(room.getId(), request(start, true), actor);
        long afterSuccessfulBooking = confirmations.count();

        assertThatThrownBy(() -> reservations.createReservation(room.getId(), request(start, true), actor))
                .isInstanceOf(ConflictException.class);
        assertThat(confirmations.count()).isEqualTo(afterSuccessfulBooking);
    }

    @Test
    void smtpOutageLeavesReservationReservedAndNeverRetriesTerminalFailure() {
        Instant start = Instant.now().plusSeconds(7200);
        var created = reservations.createReservation(room.getId(), request(start, true), actor);
        org.mockito.Mockito.doThrow(new BookingConfirmationMailGateway.SubmissionException(
                new RuntimeException("private smtp outage detail")))
                .when(gateway).send(org.mockito.ArgumentMatchers.any());
        var worker = new BookingConfirmationWorker(queue, users, gateway);

        worker.processPending();

        var confirmation = confirmations.findByReservationId(created.id()).orElseThrow();
        assertThat(confirmation.getStatus()).isEqualTo(BookingConfirmationStatus.FAILED);
        assertThat(reservations.getReservation(created.id()).status()).isEqualTo(at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED);
        worker.processPending();
        org.mockito.Mockito.verify(gateway).send(org.mockito.ArgumentMatchers.any());
    }

    private ReservationCreateRequest request(Instant start, boolean emailNotification) {
        return new ReservationCreateRequest(start, start.plusSeconds(3600), seating.getId(), 4,
                List.of(), null, "Team", emailNotification);
    }
}
