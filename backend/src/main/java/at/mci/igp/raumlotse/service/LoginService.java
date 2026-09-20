package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    public record Result(AuthenticatedUser user, LoginAttemptService.Outcome outcome, int retryAfterSeconds) { }
    private static final String CSRF_SESSION_ATTRIBUTE = HttpSessionCsrfTokenRepository.class.getName() + ".CSRF_TOKEN";
    private final LoginAttemptService attempts;
    private final HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public LoginService(LoginAttemptService attempts) { this.attempts = attempts; }

    public Result login(String email, String password, HttpServletRequest request,
            HttpServletResponse response) {
        var result = attempts.authenticate(email, password);
        if (result.outcome() != LoginAttemptService.Outcome.SUCCESS) {
            return new Result(null, result.outcome(), result.retryAfterSeconds());
        }
        try {
            return new Result(establishSession(result.account(), request, response), result.outcome(), 0);
        } catch (RuntimeException ex) {
            SecurityContextHolder.clearContext();
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            throw ex;
        }
    }

    private AuthenticatedUser establishSession(UserAccount account, HttpServletRequest request,
            HttpServletResponse response) {
        request.changeSessionId();
        var session = request.getSession(false);
        if (session != null) session.removeAttribute(CSRF_SESSION_ATTRIBUTE);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        var principal = new AuthenticatedUser(account.getId(), account.getDisplayName());
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return principal;
    }
}
