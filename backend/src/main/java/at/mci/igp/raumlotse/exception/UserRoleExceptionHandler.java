package at.mci.igp.raumlotse.exception;
import at.mci.igp.raumlotse.controller.*;
import at.mci.igp.raumlotse.dto.Problem;
import org.slf4j.*;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(assignableTypes={UserRoleController.class,CurrentRolesController.class,AdminModeController.class,RolePermissionController.class})
@Order(-10)
public class UserRoleExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(UserRoleExceptionHandler.class);
    @ExceptionHandler(UserRoleException.class)
    public ResponseEntity<Problem> role(UserRoleException ex) { return response(ex.getStatus(),ex.getCode(),ex.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> validation(MethodArgumentNotValidException ex) {
        boolean roles=ex.getBindingResult().getFieldErrors().stream().anyMatch(e->e.getField().startsWith("roles"));
        boolean permissions=ex.getBindingResult().getFieldErrors().stream().anyMatch(e->e.getField().startsWith("permissions"));
        return response(400,roles?"INVALID_ROLE_SELECTION":permissions?"INVALID_PERMISSION_SELECTION":"INVALID_REQUEST",
                roles?"Wählen Sie ein bis fünf verschiedene unterstützte Rollen.":permissions?"Die Rechteauswahl ist ungültig.":"Eine gültige erwartete Version ist erforderlich.");
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    public ResponseEntity<Problem> invalid(Exception ex) { return response(400,"INVALID_REQUEST","Die Anfrage ist ungültig."); }
    @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class,jakarta.persistence.PersistenceException.class})
    public ResponseEntity<Problem> storage(Exception ex) { return response(503,"ROLE_MANAGEMENT_UNAVAILABLE","Die Rollenverwaltung ist vorübergehend nicht verfügbar."); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> unexpected(Exception ex) { return response(500,"INTERNAL_ERROR","Die Anfrage konnte nicht abgeschlossen werden."); }
    private ResponseEntity<Problem> response(int status,String code,String detail) {
        log.warn("role_request_failed code={} status={}",code,status);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .body(Problem.of(status,HttpStatus.valueOf(status).getReasonPhrase(),detail,code));
    }
}

