package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.*;
import at.mci.igp.raumlotse.dto.UserRoleUpdateRequest;
import at.mci.igp.raumlotse.exception.UserRoleException;
import at.mci.igp.raumlotse.service.UserRoleService;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserRoleConcurrentUpdateIntegrationTest extends AbstractIntegrationTest {
    @Autowired UserRoleService service;
    @Autowired JdbcTemplate db;
    @Autowired DataSource source;
    UUID first, second, target;
    @BeforeEach void fixtures() {
        db.update("delete from role_assignment"); db.update("delete from user_role_state"); db.update("delete from user_account");
        first=account("ADMIN"); second=account("ADMIN"); target=account("VIEWER");
    }
    UUID account(String role) {
        UUID id=UUID.randomUUID();
        db.update("insert into user_account(id,email,display_name,password_hash) values (?,?,?,?)",
            id,id+"@example.test","Race fixture","{pbkdf2-sha256-600000-v1}test-fixture-not-a-real-password");
        db.update("insert into user_role_state(user_id) values (?)",id);
        db.update("insert into role_assignment values (?,?)",id,role);
        return id;
    }
    int save(UUID actor,UUID id,String role) {
        try { service.replace(actor,id.toString(),new UserRoleUpdateRequest(List.of(role),"0")); return 200; }
        catch(UserRoleException ex) { return ex.getStatus(); }
    }
    @Test void competingSameTargetEditsHaveExactlyOneWinner() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CyclicBarrier(2);
            var a=pool.submit(()->{start.await();return save(first,target,"STUDENT");});
            var b=pool.submit(()->{start.await();return save(second,target,"LECTURER");});
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isEqualTo(1);
            assertThat(db.queryForObject("select count(*) from role_assignment where user_id=?",Integer.class,target)).isEqualTo(1);
        }
    }
    @Test void concurrentSelfDemotionsPreserveLastAdmin() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CyclicBarrier(2);
            var a=pool.submit(()->{start.await();return save(first,first,"VIEWER");});
            var b=pool.submit(()->{start.await();return save(second,second,"VIEWER");});
            var outcomes=List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder(200,409);
            assertThat(db.queryForObject("select count(*) from role_assignment where role_code='ADMIN'",Integer.class)).isEqualTo(1);
        }
    }
    @Test void actorRevokedWhileWaitingIsDeniedAfterAcquiringGuard() throws Exception {
        try(Connection connection=source.getConnection(); var pool=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select id from role_mutation_guard where id=1 for update");
            var waiter=pool.submit(()->save(first,target,"STUDENT"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            boolean waiting=false;
            while(System.nanoTime()<deadline) {
                waiting=Boolean.TRUE.equals(db.queryForObject("""
                    select exists(select 1 from pg_stat_activity where wait_event_type='Lock'
                    and query like '%role_mutation_guard%' and pid<>pg_backend_pid())
                    """,Boolean.class));
                if(waiting) break;
                Thread.onSpinWait();
            }
            assertThat(waiting).as("Waiting role writer observed through PostgreSQL lock state").isTrue();
            try(var revoke=connection.prepareStatement("delete from role_assignment where user_id=? and role_code='ADMIN'")) {
                revoke.setObject(1,first); revoke.executeUpdate();
            }
            try(var viewer=connection.prepareStatement("insert into role_assignment values (?, 'VIEWER')")) {
                viewer.setObject(1,first); viewer.executeUpdate();
            }
            connection.commit();
            assertThat(waiter.get(5,TimeUnit.SECONDS)).isEqualTo(403);
            assertThat(db.queryForList("select role_code from role_assignment where user_id=?",String.class,target)).containsExactly("VIEWER");
        }
    }
    @Test void assignmentFailureRollsBackDeletionAndVersion() {
        db.execute("""
            create function test_reject_assignment() returns trigger language plpgsql as $$
            begin raise exception 'TEST_STORAGE_FAILURE'; end $$;
            """);
        db.execute("create trigger test_reject_assignment before insert on role_assignment for each row execute function test_reject_assignment()");
        try {
            assertThatThrownBy(()->service.replace(first,target.toString(),new UserRoleUpdateRequest(List.of("STUDENT"),"0")))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            db.execute("drop trigger test_reject_assignment on role_assignment");
            db.execute("drop function test_reject_assignment()");
        }
        assertThat(db.queryForList("select role_code from role_assignment where user_id=?",String.class,target)).containsExactly("VIEWER");
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isZero();
    }
    @Test void lockWaitIsBoundedAndDoesNotReplayTheWrite() throws Exception {
        try(Connection connection=source.getConnection()) {
            connection.setAutoCommit(false);
            connection.createStatement().execute("select id from role_mutation_guard where id=1 for update");
            long start=System.nanoTime();
            assertThatThrownBy(()->service.replace(first,target.toString(),new UserRoleUpdateRequest(List.of("STUDENT"),"0")))
                .isInstanceOf(RuntimeException.class);
            long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertThat(millis).isBetween(4500L,10000L);
            connection.rollback();
        }
        assertThat(db.queryForObject("select roles_version from user_role_state where user_id=?",Long.class,target)).isZero();
    }
}
