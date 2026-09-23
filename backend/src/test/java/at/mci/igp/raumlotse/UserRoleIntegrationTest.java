package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserRoleIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    UUID admin, target;
    static final List<String> ROLES = List.of("ADMIN","UNIVERSITY_STAFF","STUDENT","LECTURER","VIEWER");

    @BeforeEach void fixtures() {
        db.update("delete from role_assignment");
        db.update("delete from user_role_state");
        db.update("delete from user_account");
        admin = account("Admin", "admin@example.test", List.of("ADMIN"));
        target = account("Target", "target@example.test", List.of("VIEWER"));
    }
    UUID account(String name, String email, List<String> roles) {
        UUID id = UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
            id,email,name,"{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        db.update("insert into user_role_state(user_id) values (?)",id);
        for (String role : roles) db.update("insert into role_assignment(user_id,role_code) values (?,?)",id,role);
        return id;
    }
    RequestPostProcessor actor(UUID id) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(id,"Verified fixture"),null,List.of()));
    }
    String path(UUID id) { return "/api/admin/users/"+id+"/roles"; }
    String payload(List<String> roles, long version) {
        return "{\"roles\":[\""+String.join("\",\"",roles)+"\"],\"expectedVersion\":\""+version+"\"}";
    }
    @Test void all31CombinationsPersistAndNoopKeepsVersion() throws Exception {
        long version=0;
        for (int mask=1;mask<32;mask++) {
            List<String> selection=new ArrayList<>();
            for(int i=0;i<5;i++) if((mask & (1<<i))!=0) selection.add(ROLES.get(i));
            mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json")
                .content(payload(selection,version))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(selection)).andExpect(header().string("Cache-Control","no-store"));
            version++;
            mvc.perform(get(path(target)).with(actor(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(selection)).andExpect(jsonPath("$.version").value(""+version))
                .andExpect(jsonPath("$.availableRoles.length()").value(5));
            mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json")
                .content(payload(selection,version))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(""+version));
        }
    }
    @Test void staleNoopAndChangeAwayAndBackRejectOldVersions() throws Exception {
        for (var selection : List.of(List.of("STUDENT"),List.of("VIEWER"))) {
            long v = db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target);
            mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json")
                .content(payload(selection,v))).andExpect(status().isOk());
        }
        mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json")
            .content(payload(List.of("VIEWER"),0))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ROLES"));
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isEqualTo(2);
    }
    @Test void authorizationUsesCurrentRolesAndProtectsUnknownTargets() throws Exception {
        mvc.perform(get(path(target))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        for(int mask=1;mask<16;mask++) {
            db.update("delete from role_assignment where user_id=?",target);
            for(int i=0;i<4;i++) if((mask&(1<<i))!=0) db.update("insert into role_assignment values (?,?)",target,ROLES.get(i+1));
            mvc.perform(get(path(UUID.randomUUID())).with(actor(target))).andExpect(status().isForbidden());
            mvc.perform(put(path(target)).with(actor(target)).with(csrf()).contentType("application/json")
                .content(payload(List.of("ADMIN"),0))).andExpect(status().isForbidden());
        }
        db.update("insert into role_assignment values (?, 'ADMIN')",target);
        mvc.perform(get(path(admin)).with(actor(target))).andExpect(status().isOk());
        mvc.perform(put(path(admin)).with(actor(target)).with(csrf()).contentType("application/json")
            .content(payload(List.of("VIEWER"),0))).andExpect(status().isOk());
        mvc.perform(get(path(target)).with(actor(admin))).andExpect(status().isForbidden());
    }
    @Test void lastAdminAndInvalidSelectionsNeverChangeState() throws Exception {
        mvc.perform(put(path(admin)).with(actor(admin)).with(csrf()).contentType("application/json")
            .content(payload(List.of("VIEWER"),0))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        for (String body: List.of("{}", "{\"roles\":[],\"expectedVersion\":\"0\"}",
            "{\"roles\":null,\"expectedVersion\":\"0\"}", "{\"roles\":[null],\"expectedVersion\":\"0\"}",
            "{\"roles\":[\"VIEWER\",\"VIEWER\"],\"expectedVersion\":\"0\"}",
            "{\"roles\":[\"UNKNOWN\"],\"expectedVersion\":\"0\"}",
            "{\"roles\":[\"VIEWER\"],\"expectedVersion\":\"01\"}",
            "{\"roles\":[\"VIEWER\"],\"expectedVersion\":\"9223372036854775808\"}")) {
            mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?",String.class,target)).containsExactly("VIEWER");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isZero();
        mvc.perform(put(path(target)).with(actor(admin)).contentType("application/json").content(payload(List.of("ADMIN"),0)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
    @Test void directorySearchIsLiteralBoundedAndStable() throws Exception {
        account("Percent %", "percent@example.test",List.of("VIEWER"));
        mvc.perform(get("/api/admin/users").param("q"," % ").with(actor(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].displayName").value("Percent %"));
        mvc.perform(get("/api/admin/users").param("page","99").with(actor(admin))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.totalElements").value(3));
        for(String size: List.of("0","101","bad")) mvc.perform(get("/api/admin/users").param("size",size).with(actor(admin)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(path(UUID.randomUUID())).with(actor(admin))).andExpect(status().isNotFound());
    }
    @Test void databaseRejectsDuplicateUnknownAndOrphanAssignments() {
        assertThatThrownBy(()->db.update("insert into role_assignment values (?, 'VIEWER')",target)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->db.update("insert into role_assignment values (?, 'INVALID')",target)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->db.update("insert into role_assignment values (?, 'VIEWER')",UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(db.queryForObject("select count(*) from role_mutation_guard where id=1",Integer.class)).isEqualTo(1);
    }

    @Test void versionMustBeJsonTextAndUnauthorizedMalformedBodiesDoNotDiscloseValidation() throws Exception {
        mvc.perform(put(path(target)).with(actor(admin)).with(csrf()).contentType("application/json")
            .content("{\"roles\":[\"STUDENT\"],\"expectedVersion\":0}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(put(path(UUID.randomUUID())).with(actor(target)).with(csrf()).contentType("application/json")
            .content("{invalid")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_REQUIRED"));
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isZero();
    }

    @Test void everyAdminCombinationHasAccessAndMembershipIsCurrent() throws Exception {
        for(int mask=0;mask<16;mask++) {
            db.update("delete from role_assignment where user_id=?",target);
            db.update("insert into role_assignment values (?, 'ADMIN')",target);
            for(int i=0;i<4;i++) if((mask&(1<<i))!=0) db.update("insert into role_assignment values (?,?)",target,ROLES.get(i+1));
            mvc.perform(get("/api/admin/users").with(actor(target))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/auth/roles").with(actor(target))).andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("ADMIN")).andExpect(jsonPath("$.ready").value(true));
        db.update("delete from role_assignment where user_id=? and role_code='ADMIN'",target);
        mvc.perform(get("/api/auth/roles").with(actor(target))).andExpect(status().isOk())
            .andExpect(jsonPath("$.roles.length()").value(4));
    }

    @Test void readinessBlocksUseWhileProvisioningLeavesAnAccountRoleless() throws Exception {
        db.update("delete from role_assignment where user_id=?",target);
        mvc.perform(get("/api/admin/users").with(actor(admin))).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ROLE_MANAGEMENT_UNAVAILABLE"));
        mvc.perform(get("/api/auth/roles").with(actor(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
    }

    @Test void realLoginSessionUsesPersistedAuthorityAndRevocationWithoutRelogin() throws Exception {
        db.update("update user_account set password_hash=? where id=?",passwordEncoder.encode("Test-only password"),admin);
        var boot=mvc.perform(get("/api/auth/csrf")).andReturn();
        String token=com.jayway.jsonpath.JsonPath.read(boot.getResponse().getContentAsString(),"$.token");
        var login=mvc.perform(post("/api/auth/login")
            .session((org.springframework.mock.web.MockHttpSession)boot.getRequest().getSession(false))
            .header("X-CSRF-TOKEN",token).contentType("application/json")
            .content("{\"email\":\"admin@example.test\",\"password\":\"Test-only password\"}"))
            .andExpect(status().isOk()).andReturn();
        var session=(org.springframework.mock.web.MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isOk());
        db.update("insert into role_assignment values (?, 'ADMIN')",target);
        mvc.perform(put(path(admin)).with(actor(target)).with(csrf()).contentType("application/json")
            .content(payload(List.of("VIEWER"),0))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").session(session)).andExpect(status().isOk());
    }

    @Test void realNonAdminLoginKeepsPersistedRolesAndIgnoresBrowserAuthorityClaims() throws Exception {
        String password = "Test-only student password";
        db.update("update user_account set password_hash=? where id=?", passwordEncoder.encode(password), target);
        long versionBefore = db.queryForObject("select roles_version from user_role_state where user_id=?", Long.class, target);

        var bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/login")
                .session((org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false))
                .header("X-CSRF-TOKEN", token).contentType("application/json")
                .content("{\"email\":\"target@example.test\",\"password\":\"" + password
                        + "\",\"userId\":\"" + admin + "\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("VALIDATION_FAILED"));
        var cleanBootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String cleanToken = com.jayway.jsonpath.JsonPath.read(cleanBootstrap.getResponse().getContentAsString(), "$.token");
        var login = mvc.perform(post("/api/auth/login")
                .session((org.springframework.mock.web.MockHttpSession) cleanBootstrap.getRequest().getSession(false))
                .header("X-CSRF-TOKEN", cleanToken).contentType("application/json")
                .content("{\"email\":\"target@example.test\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.userId").value(target.toString()));
        mvc.perform(get("/api/auth/roles").session(session)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.ready").value(true));
        mvc.perform(get("/api/admin/users").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").session(session)).andExpect(status().isOk());
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?", String.class, target))
                .containsExactly("VIEWER");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?", Long.class, target))
                .isEqualTo(versionBefore);
    }
}
