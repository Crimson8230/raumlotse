package at.mci.igp.raumlotse.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.service.ReservationService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReservationOwnershipTest {
    @Mock ReservationService service;
    @Mock UserRoleSafety roles;

    private static Authentication signedIn(AuthenticatedUser user) {
        return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
    }

    @Test
    void controllerPassesAuthenticatedIdentityInsteadOfClientOwnerField() {
        var controller = new ReservationController(service, roles);
        UUID roomId = UUID.randomUUID();
        var request = new ReservationCreateRequest(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                UUID.randomUUID(), 1, List.of(), null, "attacker supplied name");
        var user = new AuthenticatedUser(UUID.randomUUID(), "Verified owner");

        controller.createReservation(roomId, request, signedIn(user));

        verify(service).createReservation(eq(roomId), eq(request), eq(user));
    }

    @Test
    void actionsCarryTheCallersIdentityAndAdministratorDecision() {
        var controller = new ReservationController(service, roles);
        var user = new AuthenticatedUser(UUID.randomUUID(), "Someone");
        UUID reservationId = UUID.randomUUID();
        when(roles.isAdmin(user.userId())).thenReturn(false);

        controller.cancelReservation(reservationId, signedIn(user));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(service).cancelReservation(eq(reservationId), actor.capture());
        assertThat(actor.getValue()).isEqualTo(new Actor(user.userId(), false));
    }

    @Test
    void administratorFlagComesFromTheRoleLookupNotFromTheRequest() {
        var controller = new ReservationController(service, roles);
        var user = new AuthenticatedUser(UUID.randomUUID(), "Admin");
        when(roles.isAdmin(user.userId())).thenReturn(true);

        controller.getReservation(UUID.randomUUID(), signedIn(user));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(service).getReservation(any(), actor.capture());
        assertThat(actor.getValue().admin()).isTrue();
    }

    @Test
    void myUpcomingUsesTheUserIdentityNotTheDisplayName() {
        var controller = new ReservationController(service, roles);
        var user = new AuthenticatedUser(UUID.randomUUID(), "Same Name");

        controller.getMyUpcomingReservations(signedIn(user));

        verify(service).getMyUpcomingReservations(user.userId());
    }

    @Test
    void principalsWithoutAnApplicationIdentityAreRejected() {
        var controller = new ReservationController(service, roles);
        Authentication nameOnly = UsernamePasswordAuthenticationToken.authenticated(
                new org.springframework.security.core.userdetails.User("legacy", "x", List.of()), null, List.of());
        Authentication anonymous = new AnonymousAuthenticationToken("k", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThatThrownBy(() -> controller.cancelReservation(UUID.randomUUID(), nameOnly))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.getReservation(UUID.randomUUID(), anonymous))
                .isInstanceOf(ResponseStatusException.class);
    }
}
