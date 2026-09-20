package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository;
import at.mci.igp.raumlotse.service.AccountAuthenticationService;
import at.mci.igp.raumlotse.service.LoginAttemptIdentity;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import at.mci.igp.raumlotse.service.LoginAttemptService;
import at.mci.igp.raumlotse.repository.LoginAttemptStateRepository.LockedState;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LoginCooldownFailureTest {
    @Test
    void lockFailureRollsBackAndNeverVerifiesOrAddsACredentialFailure() {
        var authentication = mock(AccountAuthenticationService.class);
        var states = mock(LoginAttemptStateRepository.class);
        when(states.lock(any(byte[].class))).thenThrow(new DataAccessResourceFailureException("private storage detail"));
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var service = new LoginAttemptService(authentication, states, new LoginAttemptIdentity(new byte[32]),
                new EmailCanonicalizer(), jdbc, manager);

        assertThatThrownBy(() -> service.authenticate("failure@example.test", "secret-password"))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(authentication, never()).authenticate(anyString(), anyString());
        verify(states, never()).save(any(byte[].class), any(), any(), any());
        verify(manager).rollback(any(TransactionStatus.class));
    }

    @Test
    void credentialProviderOutageRollsBackWithoutAddingAFailure() {
        var authentication = mock(AccountAuthenticationService.class);
        var states = mock(LoginAttemptStateRepository.class);
        when(states.lock(any(byte[].class))).thenReturn(new LockedState(List.of(), null, Instant.now()));
        when(authentication.authenticate(anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("private provider detail"));
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(new SimpleTransactionStatus());
        var service = new LoginAttemptService(authentication, states, new LoginAttemptIdentity(new byte[32]),
                new EmailCanonicalizer(), jdbc, manager);

        assertThatThrownBy(() -> service.authenticate("failure@example.test", "secret-password"))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(states).lock(any(byte[].class));
        verify(states, never()).save(any(byte[].class), any(), any(), any());
        verify(manager).rollback(any(TransactionStatus.class));
    }
}
