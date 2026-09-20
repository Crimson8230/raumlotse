package at.mci.igp.raumlotse.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class UserRoleReadinessCheck {
    private final JdbcTemplate db;
    public UserRoleReadinessCheck(JdbcTemplate db) { this.db=db; }
    public boolean ready() {
        return Boolean.TRUE.equals(db.queryForObject("""
            select exists(select 1 from role_assignment where role_code='ADMIN')
            and not exists(select 1 from user_account u where not exists
                (select 1 from role_assignment r where r.user_id=u.id))
            """,Boolean.class));
    }
}

