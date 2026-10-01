package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ReservationCreateRequest;
import at.mci.igp.raumlotse.service.ReservationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class ReservationOwnershipTest {
    @Mock ReservationService service;

    @Test
    void controllerPassesAuthenticatedIdentityInsteadOfClientOwnerField() {
        var controller = new ReservationController(service);
        UUID roomId = UUID.randomUUID();
        var request = new ReservationCreateRequest(Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200),
                UUID.randomUUID(), 1, List.of(), null, "attacker supplied name");
        var user = new AuthenticatedUser(UUID.randomUUID(), "Verified owner");
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());

        controller.createReservation(roomId, request, authentication);

        verify(service).createReservation(eq(roomId), eq(request), eq(user));
    }
}
