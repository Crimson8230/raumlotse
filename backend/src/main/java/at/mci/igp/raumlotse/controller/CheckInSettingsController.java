package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.CheckInSettingsResponse;
import at.mci.igp.raumlotse.dto.CheckInSettingsUpdateRequest;
import at.mci.igp.raumlotse.service.CheckInSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Admin-editable check-in times (feature 014, FR-022). Under /api/admin, so RoleAccessFilter admits administrators only. */
@RestController
public class CheckInSettingsController {
    private final CheckInSettingsService service;

    public CheckInSettingsController(CheckInSettingsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/check-in-settings")
    public CheckInSettingsResponse read() {
        return service.read();
    }

    @PutMapping("/api/admin/check-in-settings")
    public CheckInSettingsResponse update(@Valid @RequestBody CheckInSettingsUpdateRequest request,
            Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Anmeldung erforderlich.");
        }
        return service.update(request, user.userId());
    }
}
