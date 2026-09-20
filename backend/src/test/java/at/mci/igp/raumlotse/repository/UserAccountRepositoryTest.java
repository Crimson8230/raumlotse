package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserAccountRepositoryTest extends AbstractIntegrationTest {
    @Autowired UserAccountRepository repository;

    @Test
    void persistsUuidAndUniqueCanonicalEmailWithoutExposingPasswordHash() throws Exception {
        var encoded = "{pbkdf2-sha256-600000-v1}dummy-encoded-payload-long";
        var user = repository.saveAndFlush(new UserAccount("test@example.test", "Test User", encoded));
        assertThat(user.getId()).isInstanceOf(UUID.class);
        assertThat(repository.findByEmail("test@example.test")).contains(user);
        assertThatThrownBy(() -> repository.saveAndFlush(new UserAccount(" TEST@example.test ", "Other", encoded)))
                .isInstanceOf(DataIntegrityViolationException.class);

        var mapper = new tools.jackson.databind.json.JsonMapper();
        assertThat(mapper.writeValueAsString(user)).doesNotContain(encoded, "passwordHash");
        assertThat(mapper.writeValueAsString(new AuthenticatedUser(user.getId(), user.getDisplayName())))
                .doesNotContain(encoded, "passwordHash", "email");
    }

    @Test
    void databaseRequiresEmail() {
        assertThatThrownBy(() -> repository.saveAndFlush(new UserAccount(null, "Name", "{pbkdf2-sha256-600000-v1}dummy-encoded-payload-long")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRequiresDisplayName() {
        assertThatThrownBy(() -> repository.saveAndFlush(new UserAccount("other@example.test", null, "{pbkdf2-sha256-600000-v1}dummy-encoded-payload-long")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRequiresEncodedPassword() {
        assertThatThrownBy(() -> repository.saveAndFlush(new UserAccount("third@example.test", "Name", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
