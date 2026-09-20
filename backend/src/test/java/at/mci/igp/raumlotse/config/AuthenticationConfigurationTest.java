package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationConfigurationTest {
    @Test
    void hmacKeyMustBeStableBase64WithAtLeastThirtyTwoBytes() {
        var config = new LoginAttemptKeyConfig();
        byte[] key = new byte[32];
        assertThat(config.loginAttemptHmacKey(Base64.getEncoder().encodeToString(key))).hasSize(32);
        assertThatThrownBy(() -> config.loginAttemptHmacKey("" )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.loginAttemptHmacKey(Base64.getEncoder().encodeToString(new byte[31])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fixtureIsLocalOnlyAndNeverResetsAnExistingPassword() throws Exception {
        assertThat(LocalAuthFixtureConfiguration.class.getAnnotation(Profile.class).value())
                .containsExactly("local-auth-fixture");
        var accounts = mock(UserAccountRepository.class);
        var encoder = mock(PasswordEncoder.class);
        var environment = mock(org.springframework.core.env.Environment.class);
        var existing = new UserAccount("user@example.test", "Existing", "{hash}persisted");
        when(accounts.findByEmail("user@example.test")).thenReturn(Optional.of(existing));
        var runner = new LocalAuthFixtureConfiguration().provisionLocalAuthFixture(accounts, encoder,
                new EmailCanonicalizer(), environment, "USER@example.test", "Fixture", "new-password");
        runner.run(new DefaultApplicationArguments(new String[0]));
        verify(accounts, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(UserAccount.class));
        verify(encoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void fixtureRefusesProductionProfiles() {
        var environment = mock(org.springframework.core.env.Environment.class);
        when(environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production")))
                .thenReturn(true);
        assertThatThrownBy(() -> new LocalAuthFixtureConfiguration().provisionLocalAuthFixture(
                mock(UserAccountRepository.class), mock(PasswordEncoder.class), new EmailCanonicalizer(), environment,
                "user@example.test", "User", "password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Local auth fixtures cannot be enabled with a production profile.");
    }
}
