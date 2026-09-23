package at.mci.igp.raumlotse.service;
import at.mci.igp.raumlotse.dto.*;
import at.mci.igp.raumlotse.exception.UserRoleException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
@Component
public class RoleIdentityAdapter {
    private final JdbcTemplate db;
    public RoleIdentityAdapter(JdbcTemplate db) { this.db=db; }
    public static UUID actor(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser user))
            throw new UserRoleException(401,"AUTHENTICATION_REQUIRED","Authentication is required.");
        return user.userId();
    }
    public UUID target(String id) {
        try { UUID result=UUID.fromString(id); if(!result.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException(); return result; }
        catch(IllegalArgumentException ex) { throw new UserRoleException(404,"USER_NOT_FOUND","The user no longer exists."); }
    }
    public UserListResponse list(String query,int page,int size) {
        String q=query==null?"":query.strip();
        if(q.length()>100 || page<0 || size<1 || size>100)
            throw new UserRoleException(400,"INVALID_REQUEST","Invalid search or page parameters.");
        String literal=q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");
        String pattern="%"+literal+"%";
        String where=" where lower(display_name) like ? escape '!' or lower(email) like ? escape '!'";
        long total=db.queryForObject("select count(*) from user_account"+where,Long.class,pattern,pattern);
        var rows=db.query("select id,display_name,email from user_account"+where+" order by display_name,id limit ? offset ?",
            (rs,n)->new UserSummary(rs.getString("id"),rs.getString("display_name"),rs.getString("email")),
            pattern,pattern,size,(long)page*size);
        return new UserListResponse(rows,page,size,total);
    }
}

