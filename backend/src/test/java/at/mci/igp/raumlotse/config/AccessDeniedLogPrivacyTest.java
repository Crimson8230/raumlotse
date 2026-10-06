package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.RoleAccessFilter;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** Feature 013, FR-026: refusals are logged with category, method, route pattern and user id only. */
class AccessDeniedLogPrivacyTest {

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void attach() {
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detach() {
        root.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    @Test
    @SuppressWarnings("unchecked")
    void adminRefusalLogsRoutePatternAndUserIdButNoTargetIdOrName() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserRoleSafety safety = mock(UserRoleSafety.class);
        doThrow(new UserRoleException(403, "ADMIN_REQUIRED", "Administratorrechte erforderlich."))
                .when(safety).requireAdmin(any());
        ObjectProvider<UserRoleSafety> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(safety);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, "Secret Display Name"), null, List.of()));
        var request = new MockHttpServletRequest("DELETE", "/api/rooms/" + targetId);
        request.setServletPath("/api/rooms/" + targetId);

        new RoleAccessFilter(provider, JsonMapper.builder().build())
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).anyMatch(m -> m.contains("access_denied category=admin method=DELETE")
                && m.contains("route=/api/rooms/{id}") && m.contains("userId=" + userId));
        assertThat(messages).noneMatch(m -> m.contains(targetId.toString()) || m.contains("Secret Display Name"));
    }
}
