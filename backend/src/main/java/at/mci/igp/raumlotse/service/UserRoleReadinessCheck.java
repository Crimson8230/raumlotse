package at.mci.igp.raumlotse.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
@Component
public class UserRoleReadinessCheck {
    private final JdbcTemplate db;
    public UserRoleReadinessCheck(JdbcTemplate db) { this.db=db; }
    public boolean ready() {
        try {
            return Boolean.TRUE.equals(db.queryForObject("""
            select exists(select 1 from role_assignment where role_code='ADMIN')
            and not exists(select 1 from user_account u where not exists
                (select 1 from role_assignment r where r.user_id=u.id))
            and (select count(*) from role_permission_state)=5
            and not exists(select 1 from role_permission_state s
                where s.role_code not in ('ADMIN','UNIVERSITY_STAFF','STUDENT','LECTURER','VIEWER')
                or (exists(select 1 from role_permission p where p.role_code=s.role_code and p.permission_code<>'READ')
                    and not exists(select 1 from role_permission p where p.role_code=s.role_code and p.permission_code='READ')))
            """,Boolean.class));
        } catch (org.springframework.dao.DataAccessException unavailable) {
            return false;
        }
    }
}

