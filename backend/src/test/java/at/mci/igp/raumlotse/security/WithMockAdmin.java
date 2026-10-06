package at.mci.igp.raumlotse.security;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Signs the test in as the application's {@link AuthenticatedUser}. Pair it with a mocked
 * {@code UserRoleSafety} (which accepts every actor as administrator) in controller slice tests.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithMockAdmin.Factory.class)
public @interface WithMockAdmin {

    class Factory implements WithSecurityContextFactory<WithMockAdmin> {
        @Override
        public SecurityContext createSecurityContext(WithMockAdmin annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedUser(UUID.randomUUID(), "Admin"), null, List.of()));
            return context;
        }
    }
}
