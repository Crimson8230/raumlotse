package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.dto.Actor;
import at.mci.igp.raumlotse.exception.UserRoleException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** Feature 013, US2: administration mode is a per-session flag that only administrators can hold (FR-004 – FR-008). */
class AdminModeServiceTest {

    private final AdminModeService service = new AdminModeService();
    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private final Actor regular = new Actor(admin.userId(), false);

    @Test
    void newSessionStartsWithTheModeOff() {
        assertThat(service.effective(new MockHttpSession(), admin)).isFalse();
        assertThat(service.effective(null, admin)).isFalse();
    }

    @Test
    void administratorCanSwitchTheModeOnAndOff() {
        var session = new MockHttpSession();

        assertThat(service.set(session, admin, true)).isTrue();
        assertThat(service.effective(session, admin)).isTrue();
        assertThat(service.set(session, admin, false)).isFalse();
        assertThat(service.effective(session, admin)).isFalse();
    }

    @Test
    void nonAdministratorCannotSwitchItOnAndItStaysOff() {
        var session = new MockHttpSession();

        assertThatThrownBy(() -> service.set(session, regular, true))
                .isInstanceOfSatisfying(UserRoleException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(403);
                    assertThat(ex.getCode()).isEqualTo("ADMIN_MODE_NOT_ALLOWED");
                });
        assertThat(service.effective(session, regular)).isFalse();
        assertThat(session.getAttribute(AdminModeService.ATTRIBUTE)).isNull();
    }

    @Test
    void managementPermissionAllowsModeWithoutAdminRole() {
        var session = new MockHttpSession();
        assertThat(service.set(session, regular.userId(), true, true)).isTrue();
        assertThat(service.effective(session, true)).isTrue();
        assertThat(service.effective(session, false)).isFalse();
    }

    @Test
    void lossOfTheAdministratorRoleEndsTheModeAndClearsTheFlag() {
        var session = new MockHttpSession();
        service.set(session, admin, true);

        assertThat(service.effective(session, regular)).isFalse();
        assertThat(session.getAttribute(AdminModeService.ATTRIBUTE)).isNull();
        // Regaining the role later does not silently bring the mode back.
        assertThat(service.effective(session, admin)).isFalse();
    }

    @Test
    void clearRemovesTheFlag() {
        var session = new MockHttpSession();
        service.set(session, admin, true);

        service.clear(session);

        assertThat(service.effective(session, admin)).isFalse();
    }
}
