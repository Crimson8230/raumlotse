package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RolePermissionMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Test
    void seedsFiveFixedRolesAndPreservesAssignmentsThenDefaultsNewAccountsToViewer() {
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("15").load().migrate();
        var db = new JdbcTemplate(source);
        UUID existing = insertAccount(db);
        db.update("insert into user_role_state(user_id,roles_version) values (?,4)", existing);
        db.update("insert into role_assignment(user_id,role_code) values (?, 'STUDENT')", existing);

        Flyway.configure().dataSource(source).load().migrate();

        assertThat(db.queryForList("select role_code from role_permission_state order by role_code", String.class))
                .containsExactly("ADMIN", "LECTURER", "STUDENT", "UNIVERSITY_STAFF", "VIEWER");
        assertThat(db.queryForList("select distinct permissions_version from role_permission_state", Long.class))
                .containsExactly(0L);
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?", String.class, existing))
                .containsExactly("STUDENT");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?", Long.class, existing))
                .isEqualTo(4L);
        for (String role : PermissionTestData.ROLES) {
            List<String> actual = db.queryForList(
                    "select permission_code from role_permission where role_code=? order by permission_code",
                    String.class, role);
            assertThat(actual).containsExactlyInAnyOrderElementsOf(PermissionTestData.INITIAL.get(role));
        }
        assertThat(db.queryForObject("select count(*) from role_permission", Integer.class)).isEqualTo(27);
        assertThatThrownBy(() -> db.update(
                "insert into role_permission(role_code,permission_code) values ('VIEWER','UNKNOWN')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> db.update(
                "insert into role_permission(role_code,permission_code) values ('VIEWER','READ')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> db.update(
                "update role_permission_state set permissions_version=-1 where role_code='VIEWER'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> db.update(
                "insert into role_permission_state(role_code) values ('CUSTOM')"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        UUID fresh = insertAccount(db);
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?", String.class, fresh))
                .containsExactly("VIEWER");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?", Long.class, fresh))
                .isZero();
    }

    private UUID insertAccount(JdbcTemplate db) {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, id + "@example.test", "Permission fixture", "{pbkdf2-sha256-600000-v1}permission-fixture-hash");
        return id;
    }
}
