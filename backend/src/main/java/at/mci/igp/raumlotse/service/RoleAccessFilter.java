package at.mci.igp.raumlotse.service;
import at.mci.igp.raumlotse.dto.Problem;
import at.mci.igp.raumlotse.exception.UserRoleException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
// Runs after verified session/account resolution, before MVC deserializes any user payload.
public class RoleAccessFilter extends OncePerRequestFilter {
    private final ObjectProvider<UserRoleSafety> safety;
    private final ObjectMapper mapper;
    public RoleAccessFilter(ObjectProvider<UserRoleSafety> safety,ObjectMapper mapper) { this.safety=safety; this.mapper=mapper; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI();
        return !(path.equals("/api/admin/users") || path.startsWith("/api/admin/users/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        response.setHeader("Cache-Control","no-store");
        try {
            var actor=RoleIdentityAdapter.actor(SecurityContextHolder.getContext().getAuthentication());
            var checks=safety.getIfAvailable();
            if(checks==null) throw UserRoleException.unavailable();
            checks.requireAdmin(actor);
        } catch(UserRoleException ex) {
            deny(response,ex.getStatus(),ex.getCode(),ex.getMessage()); return;
        } catch(RuntimeException ex) {
            deny(response,503,"ROLE_MANAGEMENT_UNAVAILABLE","Role management is temporarily unavailable."); return;
        }
        chain.doFilter(request,response);
    }
    private void deny(HttpServletResponse response,int status,String code,String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(),Problem.of(status,
            org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase(),message,code));
    }
}

