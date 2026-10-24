package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.dto.ThemePreferenceRequest;
import at.mci.igp.raumlotse.dto.ThemePreferenceResponse;
import at.mci.igp.raumlotse.service.ThemePreferenceService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/theme-preference")
public class ThemePreferenceController {
    private static final Logger log = LoggerFactory.getLogger(ThemePreferenceController.class);
    private final ThemePreferenceService preferences;

    public ThemePreferenceController(ThemePreferenceService preferences) { this.preferences = preferences; }

    @GetMapping
    public ResponseEntity<?> read(Authentication authentication) {
        try {
            return ok(preferences.read(user(authentication).userId()));
        } catch (DataAccessException ex) {
            return unavailable();
        }
    }

    @PutMapping(consumes = "application/json")
    public ResponseEntity<?> choose(@Valid @RequestBody ThemePreferenceRequest request, Authentication authentication) {
        try {
            return ok(preferences.choose(user(authentication).userId(), request.getTheme()));
        } catch (DataAccessException ex) {
            return unavailable();
        }
    }

    @PostMapping("/skip")
    public ResponseEntity<?> skip(Authentication authentication) {
        try {
            return ok(preferences.skip(user(authentication).userId()));
        } catch (DataAccessException ex) {
            return unavailable();
        }
    }

    private static AuthenticatedUser user(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser actor)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return actor;
    }

    private static ResponseEntity<ThemePreferenceResponse> ok(ThemePreferenceResponse preference) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(preference);
    }

    private static ResponseEntity<Problem> unavailable() {
        log.warn("theme_preference_failure code=THEME_PREFERENCE_UNAVAILABLE status=503");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(Problem.of(503, "Service Unavailable", "Farbschema ist vorübergehend nicht verfügbar.",
                        "THEME_PREFERENCE_UNAVAILABLE"));
    }
}
