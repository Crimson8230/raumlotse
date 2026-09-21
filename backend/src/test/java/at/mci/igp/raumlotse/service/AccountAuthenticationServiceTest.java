package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class AccountAuthenticationServiceTest {
    record EmailVector(String input, String canonical) { }

    @Test
    void sharesEmailPolicyWithTheFrontendAndProvisioning() throws Exception {
        var vectors = new tools.jackson.databind.json.JsonMapper().readValue(
                java.nio.file.Files.readString(java.nio.file.Path.of("../test-fixtures/login-emails.json")), EmailVector[].class);
        for (var vector : vectors) {
            if (vector.canonical() == null) {
                assertThatThrownBy(() -> canonicalizer.canonicalize(vector.input())).isInstanceOf(IllegalArgumentException.class);
            } else {
                assertThat(canonicalizer.canonicalize(vector.input())).isEqualTo(vector.canonical());
            }
        }
    }
    private final EmailCanonicalizer canonicalizer = new EmailCanonicalizer();

    @Test
    void canonicalizesEmailOnceAndRejectsInvalidOrOversizedAddresses() {
        assertThat(canonicalizer.canonicalize("  USER@Example.Test  ")).isEqualTo("user@example.test");
        assertThatThrownBy(() -> canonicalizer.canonicalize(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalize("x".repeat(250) + "@a.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesPasswordCharactersAndDistinguishesValuesPastByte72() {
        var encoder = new at.mci.igp.raumlotse.config.PasswordEncoderConfig().passwordEncoder();
        String first = "a".repeat(72) + "x";
        String second = "a".repeat(72) + "y";
        String encoded = encoder.encode(first);
        assertThat(encoder.matches(first, encoded)).isTrue();
        assertThat(encoder.matches(second, encoded)).isFalse();
        assertThat(encoder.matches("  " + first + "  ", encoded)).isFalse();
        assertThat("x".repeat(1024)).hasSize(1024);
    }

    @Test
    void userEntitySerializationNeverIncludesItsStoredPasswordHash() throws Exception {
        var account = new at.mci.igp.raumlotse.domain.UserAccount("user@example.test", "Test", "{pbkdf2}private-hash");
        String serialized = new tools.jackson.databind.json.JsonMapper().writeValueAsString(account);
        assertThat(serialized).doesNotContain("private-hash", "passwordHash");
    }

    @Test
    void unknownEmailStillRunsPasswordVerificationAgainstDummyHash() {
        var repository = mock(at.mci.igp.raumlotse.repository.UserAccountRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("{dummy}hash");
        when(repository.findByEmail("missing@example.test")).thenReturn(java.util.Optional.empty());
        var service = new AccountAuthenticationService(repository, encoder, canonicalizer);

        assertThat(service.authenticate("missing@example.test", "exact password")).isEmpty();
        verify(encoder).matches(eq("exact password"), eq("{dummy}hash"));
    }

    @Test
    void unusableStoredHashFailsClosedAsAnOperationalError() {
        var repository = mock(at.mci.igp.raumlotse.repository.UserAccountRepository.class);
        var encoder = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("{dummy}hash");
        var account = new at.mci.igp.raumlotse.domain.UserAccount("user@example.test", "User", "plain-text");
        when(repository.findByEmail("user@example.test")).thenReturn(java.util.Optional.of(account));
        var service = new AccountAuthenticationService(repository, encoder, canonicalizer);

        assertThatThrownBy(() -> service.authenticate("user@example.test", "password"))
                .isInstanceOf(at.mci.igp.raumlotse.exception.AuthenticationUnavailableException.class);
    }
}
