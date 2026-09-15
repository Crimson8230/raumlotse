package at.mci.igp.raumlotse.exception;

import at.mci.igp.raumlotse.dto.Problem;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Problem> handleNotFound(NotFoundException ex) {
        log.warn("not_found detail={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Problem.of(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Problem> handleConflict(ConflictException ex) {
        log.warn("conflict detail={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Problem.of(409, "Conflict", ex.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Problem> handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.warn("stale_write detail={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Problem.of(409, "Conflict", "The resource was modified by someone else since it was loaded. Reload and try again."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Problem> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("data_integrity_violation detail={}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Problem.of(409, "Conflict", "The request conflicts with an existing record (e.g. a duplicate name)."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleValidation(MethodArgumentNotValidException ex) {
        List<Problem.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Problem.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("validation_failed errors={}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Problem.of(400, "Validation Failed", "One or more fields are invalid.", errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Problem> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("bad_request detail={}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Problem.of(400, "Bad Request", ex.getMessage()));
    }
}
