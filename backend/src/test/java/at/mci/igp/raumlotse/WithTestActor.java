package at.mci.igp.raumlotse;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
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

/** MVC-slice identity matching the principal issued by local login. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithTestActor.Factory.class)
public @interface WithTestActor {
    final class Factory implements WithSecurityContextFactory<WithTestActor> {
        @Override
        public SecurityContext createSecurityContext(WithTestActor annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new AuthenticatedUser(UUID.randomUUID(), "Test actor"), null, List.of()));
            return context;
        }
    }
}
