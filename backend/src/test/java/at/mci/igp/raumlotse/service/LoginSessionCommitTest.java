package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginSessionCommitTest {
    final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    final LoginAttemptStateRepository states = mock(LoginAttemptStateRepository.class);
    final MockHttpServletRequest request = spy(new MockHttpServletRequest());
    final MockHttpServletResponse response = new MockHttpServletResponse();
    LoginService login;

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        request.getSession(true);
        var credentials = mock(AccountAuthenticationService.class);
        var account = mock(UserAccount.class);
        when(account.getId()).thenReturn(UUID.randomUUID());
        when(account.getDisplayName()).thenReturn("Test account");
        when(credentials.authenticate(anyString(), anyString())).thenReturn(Optional.of(account));
        when(states.lock(any())).thenReturn(new LoginAttemptStateRepository.LockedState(List.of(), null, Instant.EPOCH));
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT clock_timestamp()"), any(RowMapper.class))).thenReturn(Instant.EPOCH);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        login = new LoginService(new LoginAttemptService(credentials, states, new LoginAttemptIdentity(new byte[32]),
                new EmailCanonicalizer(), jdbc, transactions));
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void rotationOccursBeforeCommitButAuthenticationRemainsUnpublished() {
        String originalId = request.getSession().getId();
        doAnswer(invocation -> {
            assertThat(request.getSession().getId()).isNotEqualTo(originalId);
            assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            return null;
        }).when(transactions).commit(any());
        assertThat(login.login("user@example.test", "password", request, response).user()).isNotNull();
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
    }

    @Test void preparationFailureRollsBackAndExpiresCandidateCookie() {
        doThrow(new IllegalStateException("private preparation failure")).when(request).changeSessionId();
        assertThatThrownBy(() -> login.login("user@example.test", "password", request, response)).isInstanceOf(RuntimeException.class);
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        assertNoAuthentication();
    }

    @Test void lostCommitAcknowledgementNeverPublishesOrReplaysAuthentication() {
        doThrow(new TransactionSystemException("private commit detail")).when(transactions).commit(any());
        assertThatThrownBy(() -> login.login("user@example.test", "password", request, response)).isInstanceOf(RuntimeException.class);
        verify(transactions, times(1)).commit(any());
        verify(states, times(1)).save(any(), any(), any(), any());
        assertNoAuthentication();
    }

    @Test void publicationFailureDoesNotRestoreHistoryAfterCommit() {
        var session = spy(new MockHttpSession());
        request.setSession(session);
        doThrow(new IllegalStateException("private publication failure")).when(session)
                .setAttribute(eq("SPRING_SECURITY_CONTEXT"), any());
        assertThatThrownBy(() -> login.login("user@example.test", "password", request, response)).isInstanceOf(RuntimeException.class);
        verify(transactions).commit(any());
        verify(transactions, never()).rollback(any());
        verify(states, times(1)).save(any(), eq(List.of()), isNull(), eq(Instant.EPOCH));
        assertNoAuthentication();
    }

    private void assertNoAuthentication() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Set-Cookie")).contains("JSESSIONID=", "Max-Age=0");
    }
}
