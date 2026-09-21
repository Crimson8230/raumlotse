package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

public class CurrentAccountFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CurrentAccountFilter.class);
    private final UserAccountRepository accounts;
    private final ObjectMapper mapper;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public CurrentAccountFilter(UserAccountRepository accounts, ObjectMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || accounts == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (accounts.findById(user.userId()).isEmpty()) {
                log.warn("authentication_failure code=AUTH_REQUIRED reason=account_missing status=401");
                SecurityContextHolder.clearContext();
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
                chain.doFilter(request, response);
                return;
            }
        } catch (DataAccessException ex) {
            log.warn("authentication_failure code=AUTH_UNAVAILABLE reason=account_lookup status=503");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            mapper.writeValue(response.getOutputStream(), Problem.of(503, "Service Unavailable",
                    "Authentication is temporarily unavailable. Please try again.", "AUTH_UNAVAILABLE"));
            return;
        }
        chain.doFilter(request, response);
    }
}
