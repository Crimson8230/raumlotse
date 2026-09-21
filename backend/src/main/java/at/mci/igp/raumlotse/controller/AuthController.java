package at.mci.igp.raumlotse.controller;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.LoginRequest;
import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.service.LoginService;
import at.mci.igp.raumlotse.service.LoginAttemptService;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthController.class);
    private final LoginService loginService;
    private final EmailCanonicalizer emailCanonicalizer;

    public AuthController(LoginService loginService, EmailCanonicalizer emailCanonicalizer) {
        this.loginService = loginService;
        this.emailCanonicalizer = emailCanonicalizer;
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, Authentication current,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        if (current != null && current.isAuthenticated()
                && !(current instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return problem(HttpStatus.CONFLICT, "ALREADY_AUTHENTICATED", "You are already signed in.");
        }
        try {
            emailCanonicalizer.canonicalize(request.getEmail());
            LoginService.Result result = loginService.login(request.getEmail(), request.getPassword(),
                    servletRequest, servletResponse);
            if (result.outcome() == LoginAttemptService.Outcome.COOLDOWN) {
                log.warn("authentication_failure code=LOGIN_COOLDOWN status=429");
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("Retry-After", Integer.toString(result.retryAfterSeconds()))
                        .cacheControl(CacheControl.noStore())
                        .body(Problem.of(429, "Too Many Requests",
                                "Too many failed login attempts. Please try again after the indicated wait.",
                                "LOGIN_COOLDOWN", result.retryAfterSeconds()));
            }
            if (result.outcome() == LoginAttemptService.Outcome.INVALID_CREDENTIALS) {
                return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                        "Email address or password is incorrect.", result.retryAfterSeconds());
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.user());
        } catch (IllegalArgumentException ex) {
            return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Email or password is invalid.");
        } catch (RuntimeException ex) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_UNAVAILABLE",
                    "Sign-in is temporarily unavailable. Please try again.");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(user);
        }
        return problem(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to continue.");
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail) {
        log.warn("authentication_failure code={} status={}", code, status.value());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Problem.of(status.value(), status.getReasonPhrase(), detail, code));
    }

    private ResponseEntity<Problem> problem(HttpStatus status, String code, String detail, int retryAfterSeconds) {
        log.warn("authentication_failure code={} status={} retry_after_seconds={}", code, status.value(),
                retryAfterSeconds);
        var builder = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (retryAfterSeconds > 0)
            builder.header("Retry-After", Integer.toString(retryAfterSeconds));
        return builder.body(Problem.of(status.value(), status.getReasonPhrase(), detail, code,
                retryAfterSeconds > 0 ? retryAfterSeconds : null));
    }
}
