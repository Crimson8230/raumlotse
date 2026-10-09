package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.repository.RolePermissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RolePermissionSnapshotIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    JdbcTemplate db;
    @Autowired
    RolePermissionRepository repository;
    @Autowired
    MockMvc mvc;

    @Test
    void unionUsesCurrentAssignmentsAndCurrentRolePermissionsWithoutDuplicates() {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, id + "@example.test", "Snapshot fixture", "{pbkdf2-sha256-600000-v1}snapshot-fixture-hash");
        db.update("insert into role_assignment(user_id,role_code) values (?, 'STUDENT')", id);
        db.update("insert into role_permission(role_code,permission_code) values ('STUDENT','BUILDING_MANAGE')");
        try {
            var first = db.queryForList("""
                    select distinct p.permission_code from role_assignment a
                    join role_permission p on p.role_code=a.role_code
                    where a.user_id=? order by p.permission_code
                    """, String.class, id);
            assertThat(first).contains("READ", "RESERVE", "BUILDING_MANAGE");
            assertThat(first).doesNotHaveDuplicates();
            db.update("delete from role_permission where role_code='STUDENT' and permission_code='BUILDING_MANAGE'");
            var second = db.queryForList("""
                    select distinct p.permission_code from role_assignment a
                    join role_permission p on p.role_code=a.role_code
                    where a.user_id=? order by p.permission_code
                    """, String.class, id);
            assertThat(second).doesNotContain("BUILDING_MANAGE");
            assertThatThrownBy(() -> db.update(
                    "insert into role_permission(role_code,permission_code) values ('VIEWER','READ')"))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            db.update("delete from role_permission where role_code='STUDENT' and permission_code='BUILDING_MANAGE'");
            db.update("delete from role_assignment where user_id=?", id);
            db.update("delete from user_role_state where user_id=?", id);
            db.update("delete from user_account where id=?", id);
        }
    }

    @Test
    @Transactional
    void inconsistentOrMissingConfigurationCannotGrantPermissions() {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, id + "@example.test", "Snapshot fixture", "{pbkdf2-sha256-600000-v1}snapshot-fixture-hash");
        assertThat(repository.membership(id).permissions()).hasSize(1);

        db.update("delete from role_permission where role_code='VIEWER' and permission_code='READ'");
        db.update("insert into role_permission(role_code,permission_code) values ('VIEWER','BUILDING_MANAGE')");
        assertThatThrownBy(() -> repository.membership(id))
                .isInstanceOf(UserRoleException.class)
                .satisfies(ex -> assertThat(((UserRoleException) ex).getStatus()).isEqualTo(503));

        db.update("delete from role_permission where role_code='VIEWER'");
        db.update("delete from role_permission_state where role_code='VIEWER'");
        assertThatThrownBy(() -> repository.membership(id))
                .isInstanceOf(UserRoleException.class)
                .satisfies(ex -> assertThat(((UserRoleException) ex).getStatus()).isEqualTo(503));
    }

    @Test
    @Transactional
    void missingConfigurationForUnassignedFixedRoleFailsClosedOnProtectedRequest() throws Exception {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
                id, id + "@example.test", "Snapshot fixture", "{pbkdf2-sha256-600000-v1}snapshot-fixture-hash");
        db.update("delete from role_permission where role_code='ADMIN'");
        db.update("delete from role_permission_state where role_code='ADMIN'");

        mvc.perform(get("/api/rooms").with(SecurityMockMvcRequestPostProcessors.authentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                new AuthenticatedUser(id, "Snapshot fixture"), null, java.util.List.of()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ROLE_MANAGEMENT_UNAVAILABLE"));
    }
}
