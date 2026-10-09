package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.RolePermissionsResponse;
import at.mci.igp.raumlotse.dto.RolePermissionsUpdateRequest;
import at.mci.igp.raumlotse.service.RoleIdentityAdapter;
import at.mci.igp.raumlotse.service.RolePermissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class RolePermissionController {
    private final RolePermissionService service;

    public RolePermissionController(RolePermissionService service) {
        this.service = service;
    }

    public record Roles(List<RolePermissionsResponse.RoleOption> items) {
    }

    @GetMapping
    public ResponseEntity<Roles> roles(Authentication actor) {
        return ok(new Roles(service.roles(RoleIdentityAdapter.actor(actor))));
    }

    @GetMapping("/{roleCode}/permissions")
    public ResponseEntity<RolePermissionsResponse> read(Authentication actor, @PathVariable String roleCode) {
        return ok(service.read(RoleIdentityAdapter.actor(actor), roleCode));
    }

    @PutMapping("/{roleCode}/permissions")
    public ResponseEntity<RolePermissionsResponse> replace(Authentication actor, @PathVariable String roleCode,
            @Valid @RequestBody RolePermissionsUpdateRequest request) {
        return ok(service.replace(RoleIdentityAdapter.actor(actor), roleCode, request));
    }

    private <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
