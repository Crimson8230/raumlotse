package at.mci.igp.raumlotse.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.UserAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LoginServiceTest {
    @Test
    void rotatesTheContainerSessionAndReplacesCsrfWithAuthenticatedContext() {
        var attempts = mock(LoginAttemptService.class);
        var account = mock(UserAccount.class);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getDisplayName()).thenReturn("Test User");
        when(attempts.authenticate(org.mockito.ArgumentMatchers.eq("user@example.test"), org.mockito.ArgumentMatchers.eq("password"), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    java.util.function.Consumer<UserAccount> prepare = invocation.getArgument(2);
                    prepare.accept(account);
                    return new LoginAttemptService.Attempt(LoginAttemptService.Outcome.SUCCESS, account, 0);
                });
        var request = spy(new MockHttpServletRequest());
        var session = request.getSession(true);
        session.setAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN", "old-token");

        var result = new LoginService(attempts).login("user@example.test", "password", request,
                new MockHttpServletResponse());

        verify(request).changeSessionId();
        assertThat(result.user().displayName()).isEqualTo("Test User");
        assertThat(session.getAttribute("org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN"))
                .isNull();
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
    }
}
