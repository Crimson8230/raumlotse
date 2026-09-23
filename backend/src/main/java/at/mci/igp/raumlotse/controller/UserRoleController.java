package at.mci.igp.raumlotse.controller;
import at.mci.igp.raumlotse.dto.*;
import at.mci.igp.raumlotse.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/users")
public class UserRoleController {
    private final UserRoleService service;
    public UserRoleController(UserRoleService service) { this.service=service; }
    @GetMapping public ResponseEntity<UserListResponse> list(Authentication actor,
            @RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return ok(service.list(RoleIdentityAdapter.actor(actor),q,page,size));
    }
    @GetMapping("/{id}/roles") public ResponseEntity<UserRolesResponse> read(Authentication actor,@PathVariable String id) {
        return ok(service.read(RoleIdentityAdapter.actor(actor),id));
    }
    @PutMapping("/{id}/roles") public ResponseEntity<UserRolesResponse> replace(Authentication actor,
            @PathVariable String id,@Valid @RequestBody UserRoleUpdateRequest request) {
        return ok(service.replace(RoleIdentityAdapter.actor(actor),id,request));
    }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}

