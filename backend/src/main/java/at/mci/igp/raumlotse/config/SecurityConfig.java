package at.mci.igp.raumlotse.config;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import at.mci.igp.raumlotse.dto.Problem;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import at.mci.igp.raumlotse.service.CurrentAccountFilter;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
public class SecurityConfig {

    @Bean
    UserDetailsService noImplicitAccounts() {
        return username -> { throw new UsernameNotFoundException("No local username/password accounts are configured."); };
    }

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http, ObjectMapper objectMapper,
            org.springframework.beans.factory.ObjectProvider<UserAccountRepository> accounts,
            org.springframework.beans.factory.ObjectProvider<at.mci.igp.raumlotse.service.UserRoleSafety> roleSafety) throws Exception {
        var accountFilter = new CurrentAccountFilter(accounts.getIfAvailable(), objectMapper);
        return http
                .csrf(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .addFilterAfter(accountFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(new at.mci.igp.raumlotse.service.RoleAccessFilter(roleSafety, objectMapper), CurrentAccountFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(csrfAndAuthorizationDenialHandler(objectMapper)))
                .build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "Authentication is required.", "AUTH_REQUIRED");
    }

    private AccessDeniedHandler csrfAndAuthorizationDenialHandler(ObjectMapper objectMapper) {
        return (request, response, exception) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            boolean authenticated = authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken);
            if (exception instanceof CsrfException && !authenticated && !isPublicAuthRequest(request)) {
                writeProblem(objectMapper, response, HttpStatus.UNAUTHORIZED,
                        "Unauthorized", "Authentication is required.", "AUTH_REQUIRED");
                return;
            }
            if (exception instanceof CsrfException) {
                writeProblem(objectMapper, response, HttpStatus.FORBIDDEN,
                        "Forbidden", "The request could not be verified. Refresh and try again.", "CSRF_INVALID");
                return;
            }
            writeProblem(objectMapper, response, HttpStatus.FORBIDDEN,
                    "Forbidden", "You are not permitted to perform this action.", "FORBIDDEN");
        };
    }

    private void writeProblem(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
            HttpStatus status, String title, String detail, String code) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), Problem.of(status.value(), title, detail, code));
    }

    private boolean isPublicAuthRequest(HttpServletRequest request) {
        return ("GET".equals(request.getMethod()) && "/api/auth/csrf".equals(request.getRequestURI()))
                || ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI()));
    }
}
