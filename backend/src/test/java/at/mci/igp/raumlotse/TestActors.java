package at.mci.igp.raumlotse;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shared identities for service-level reservation tests (feature 013). */
public final class TestActors {

    public static final Actor ADMIN = new Actor(UUID.randomUUID(), true);

    private TestActors() {
    }

    /** A signed-in user whose identity is derived from the request's "reserved for" text (stable per name). */
    public static AuthenticatedUser creatorOf(ReservationCreateRequest request) {
        String name = request.reservedFor().trim();
        return new AuthenticatedUser(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    /** Like {@link #creatorOf}, and makes sure the account exists (reservations reference it by foreign key). */
    public static AuthenticatedUser registered(org.springframework.jdbc.core.JdbcTemplate jdbc,
            ReservationCreateRequest request) {
        AuthenticatedUser user = creatorOf(request);
        jdbc.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?) on conflict (id) do nothing",
                user.userId(), user.userId() + "@example.test", user.displayName(),
                "{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        return user;
    }

    /** Creates a reservation as the person named in the request's "reserved for" text. */
    public static at.mci.igp.raumlotse.dto.ReservationResponse create(
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            at.mci.igp.raumlotse.service.ReservationService service, java.util.UUID roomId,
            ReservationCreateRequest request) {
        return service.createReservation(roomId, request, registered(jdbc, request));
    }

    /** The same person as {@link #creatorOf}, as an ordinary (non-admin) actor. */
    public static Actor ownerOf(ReservationCreateRequest request) {
        return new Actor(creatorOf(request).userId(), false);
    }
}
