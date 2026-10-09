package at.mci.igp.raumlotse.service;

import static at.mci.igp.raumlotse.domain.PermissionCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.dto.RolePermissionsUpdateRequest;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RolePermissionServiceTest {
    private final RolePermissionRepository repository = mock(RolePermissionRepository.class);
    private final EffectivePermissionService effective = mock(EffectivePermissionService.class);
    private final RolePermissionService service = new RolePermissionService(repository, effective);
    private final UUID admin = UUID.randomUUID();

    @Test void changesRightsOnceAndRejectsStaleVersion() {
        when(repository.lock(Role.STUDENT)).thenReturn(4L);
        when(repository.snapshot(Role.STUDENT)).thenReturn(
                new RolePermissionRepository.RoleSnapshot(Role.STUDENT, 4, Set.of(READ)),
                new RolePermissionRepository.RoleSnapshot(Role.STUDENT, 5, Set.of(READ, BUILDING_MANAGE)));
        var updated = service.replace(admin, "STUDENT", new RolePermissionsUpdateRequest(
                List.of("READ", "BUILDING_MANAGE"), "4"));
        assertThat(updated.version()).isEqualTo("5");
        verify(repository).replace(Role.STUDENT, Set.of(READ, BUILDING_MANAGE));
        assertThatThrownBy(() -> service.replace(admin, "STUDENT", new RolePermissionsUpdateRequest(
                List.of("READ", "BUILDING_MANAGE"), "3")))
                .isInstanceOf(UserRoleException.class)
                .satisfies(ex -> assertThat(((UserRoleException) ex).getCode()).isEqualTo("STALE_ROLE_PERMISSIONS"));
        verify(repository, times(2)).lock(Role.STUDENT);
    }

    @Test void invalidSelectionCannotReachStorageMutation() {
        when(repository.lock(Role.VIEWER)).thenReturn(0L);
        when(repository.snapshot(Role.VIEWER)).thenReturn(
                new RolePermissionRepository.RoleSnapshot(Role.VIEWER, 0, Set.of(READ)));
        for (List<String> codes : List.of(List.of("BUILDING_MANAGE"), List.of("READ", "READ"),
                List.of("ROLE_MANAGEMENT"))) {
            assertThatThrownBy(() -> service.replace(admin, "VIEWER", new RolePermissionsUpdateRequest(codes, "0")))
                    .isInstanceOf(UserRoleException.class)
                    .satisfies(ex -> assertThat(((UserRoleException) ex).getCode()).isEqualTo("INVALID_PERMISSION_SELECTION"));
        }
        verify(repository, never()).replace(any(), any());
    }

    @Test void missingReadExplainsTheDependencyWithoutChangingStoredRights() {
        when(repository.lock(Role.STUDENT)).thenReturn(0L);
        var request = new RolePermissionsUpdateRequest(List.of("RESERVE"), "0");

        assertThatThrownBy(() -> service.replace(admin, "STUDENT", request))
                .isInstanceOf(UserRoleException.class)
                .hasMessageContaining("Lesen");
        verify(repository, never()).replace(any(), any());
    }
}
