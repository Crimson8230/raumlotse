package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.AbstractIntegrationTest;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/** The local fixture account must be usable as administrator without manual SQL. */
class LocalAuthFixtureRoleIntegrationTest extends AbstractIntegrationTest {

    @Autowired JdbcTemplate db;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired Environment environment;

    @BeforeEach
    void clean() {
        db.update("delete from role_assignment");
        db.update("delete from user_role_state");
        db.update("delete from user_account");
    }

    void runFixture() throws Exception {
        ApplicationRunner runner = new LocalAuthFixtureConfiguration().provisionLocalAuthFixture(accounts, encoder,
                new EmailCanonicalizer(), environment, db, "Fixture@Example.test", "Fixture", "fixture-password");
        runner.run(new DefaultApplicationArguments(new String[0]));
    }

    List<String> rolesOf(String email) {
        return db.queryForList("""
                select r.role_code from role_assignment r join user_account a on a.id = r.user_id
                where a.email = ? order by r.role_code
                """, String.class, email);
    }

    @Test
    void newFixtureAccountIsAdmin() throws Exception {
        runFixture();
        assertThat(rolesOf("fixture@example.test")).containsExactly("ADMIN");
        assertThat(db.queryForObject("select count(*) from user_role_state", Long.class)).isEqualTo(1);
    }

    @Test
    void runningTwiceKeepsASingleAdminAssignment() throws Exception {
        runFixture();
        runFixture();
        assertThat(rolesOf("fixture@example.test")).containsExactly("ADMIN");
    }

    @Test
    void existingFixtureAccountWithoutRolesBecomesAdminAndKeepsItsPassword() throws Exception {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, "fixture@example.test", "Fixture", "{pbkdf2-sha256-600000-v1}persisted-fixture-hash");

        runFixture();

        assertThat(rolesOf("fixture@example.test")).containsExactly("ADMIN");
        assertThat(db.queryForObject("select password_hash from user_account where id=?", String.class, id))
                .isEqualTo("{pbkdf2-sha256-600000-v1}persisted-fixture-hash");
    }

    @Test
    void rolesAssignedLaterAreNeverOverwritten() throws Exception {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, "fixture@example.test", "Fixture", "{pbkdf2-sha256-600000-v1}persisted-fixture-hash");
        db.update("insert into user_role_state(user_id) values (?)", id);
        db.update("insert into role_assignment(user_id,role_code) values (?,'STUDENT')", id);

        runFixture();

        assertThat(rolesOf("fixture@example.test")).containsExactly("STUDENT");
    }
}
