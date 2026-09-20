package at.mci.igp.raumlotse;
import static org.assertj.core.api.Assertions.*;
import at.mci.igp.raumlotse.service.UserRoleReadinessCheck;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class UserRoleMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17-alpine");
    @Test void backfillPreservesValidRolesAndFillsOnlyRolelessExistingAccounts() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("7").load().migrate();
        var db=new JdbcTemplate(source);
        UUID admin=UUID.randomUUID(), empty=UUID.randomUUID();
        for(UUID id:new UUID[]{admin,empty}) db.update(
            "insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
            id,id+"@example.test","Migration fixture","{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        db.update("insert into user_role_state(user_id,roles_version) values (?,4)",admin);
        db.update("insert into role_assignment values (?, 'ADMIN'), (?, 'STUDENT')",admin,admin);
        assertThat(new UserRoleReadinessCheck(db).ready()).isFalse();
        assertThatThrownBy(()->db.update("insert into role_assignment values (?, 'LEGACY_UNKNOWN')",admin))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(db.queryForList("select role_code from role_assignment where user_id=? order by role_code",String.class,admin))
            .containsExactly("ADMIN","STUDENT");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,admin)).isEqualTo(4);
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?",String.class,empty)).containsExactly("VIEWER");
        assertThat(new UserRoleReadinessCheck(db).ready()).isTrue();
        db.update("delete from role_assignment where role_code='ADMIN'");
        assertThat(new UserRoleReadinessCheck(db).ready()).isFalse();
    }
}

