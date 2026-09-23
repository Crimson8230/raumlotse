package at.mci.igp.raumlotse.repository;
import at.mci.igp.raumlotse.domain.Role;
import at.mci.igp.raumlotse.dto.*;
import at.mci.igp.raumlotse.exception.UserRoleException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class UserRoleStateRepository {
    private final JdbcTemplate db;
    public UserRoleStateRepository(JdbcTemplate db) { this.db=db; }
    // One statement gives roles and version a coherent PostgreSQL statement snapshot.
    // JDBC projections deliberately bypass JPA's first-level cache after guard acquisition.
    public UserRolesResponse snapshot(UUID id) {
        return db.query("""
            select u.id,u.display_name,u.email,s.roles_version,r.role_code
            from user_account u left join user_role_state s on s.user_id=u.id
            left join role_assignment r on r.user_id=u.id where u.id=?
            """, rs -> {
                UserSummary user=null; Long version=null; var roles=new ArrayList<Role>();
                while(rs.next()) {
                    user=new UserSummary(rs.getString("id"),rs.getString("display_name"),rs.getString("email"));
                    version=rs.getObject("roles_version",Long.class);
                    String code=rs.getString("role_code");
                    if(code!=null) roles.add(Role.valueOf(code));
                }
                if(user==null) throw new UserRoleException(404,"USER_NOT_FOUND","The user no longer exists.");
                if(version==null || roles.isEmpty()) throw UserRoleException.unavailable();
                roles.sort(Comparator.naturalOrder());
                return UserRolesResponse.of(user,List.copyOf(roles),version);
            },id);
    }
}

