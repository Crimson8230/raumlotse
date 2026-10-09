package at.mci.igp.raumlotse.service;

import static at.mci.igp.raumlotse.domain.PermissionCode.BUILDING_MANAGE;
import static at.mci.igp.raumlotse.domain.PermissionCode.READ;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;

class EffectivePermissionServiceTest {
    private final RolePermissionRepository repository = mock(RolePermissionRepository.class);
    private final EffectivePermissionService service = new EffectivePermissionService(repository);

    @Test
    void usesCurrentUnionAndKeepsRoleManagementBoundToAdmin() {
        UUID id = UUID.randomUUID();
        when(repository.membership(id)).thenReturn(new RolePermissionRepository.Membership(
                List.of(Role.STUDENT, Role.UNIVERSITY_STAFF), Set.of(READ, BUILDING_MANAGE)));
        assertThat(service.snapshot(id).permissions()).containsExactlyInAnyOrder(READ, BUILDING_MANAGE);
        assertThat(service.snapshot(id).admin()).isFalse();
        assertThat(service.snapshot(id).canUseAdminMode()).isTrue();
        service.require(id, BUILDING_MANAGE);
    }

    @Test
    void missingPermissionIsDenied() {
        UUID id = UUID.randomUUID();
        when(repository.membership(id)).thenReturn(new RolePermissionRepository.Membership(
                List.of(Role.VIEWER), Set.of(READ)));
        assertThatThrownBy(() -> service.require(id, BUILDING_MANAGE))
                .isInstanceOf(UserRoleException.class)
                .satisfies(ex -> assertThat(((UserRoleException) ex).getStatus()).isEqualTo(403));
    }

    @Test
    void sharesOneSnapshotWithinRequestAndAllowsExplicitFreshRecheck() {
        UUID id = UUID.randomUUID();
        when(repository.membership(id)).thenReturn(new RolePermissionRepository.Membership(
                List.of(Role.ADMIN), Set.of(READ)));
        var request = new MockHttpServletRequest();
        var attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            var first = service.snapshot(id, request);
            assertThat(service.snapshot(id)).isSameAs(first);
            service.require(id, READ);
            verify(repository, times(1)).membership(id);

            service.requireAdminFresh(id);
            verify(repository, times(2)).membership(id);
        } finally {
            RequestContextHolder.resetRequestAttributes();
            attributes.requestCompleted();
        }
    }
}
