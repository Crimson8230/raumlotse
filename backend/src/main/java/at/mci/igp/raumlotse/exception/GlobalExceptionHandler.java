package at.mci.igp.raumlotse.exception;

import at.mci.igp.raumlotse.dto.Problem;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
                                .body(Problem.of(409, "Conflict",
                                                "The resource was modified by someone else since it was loaded. Reload and try again."));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Problem> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
                log.warn("data_integrity_violation detail={}", ex.getMostSpecificCause().getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Problem.of(409, "Conflict",
                                                "The request conflicts with an existing record (e.g. a duplicate name)."));
        }

        private static final String INVALID_FORMAT = "has an invalid format";

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Problem> handleValidation(MethodArgumentNotValidException ex) {
                return bindingProblem(ex.getBindingResult());
        }

        /** Fallback for binding failures not raised as {@link MethodArgumentNotValidException}. */
        @ExceptionHandler(BindException.class)
        public ResponseEntity<Problem> handleBinding(BindException ex) {
                return bindingProblem(ex.getBindingResult());
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<Problem> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
                log.warn("validation_failed fields={}", ex.getName());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                                .body(Problem.of(400, "Validation Failed", "One or more parameters are invalid.",
                                                List.of(new Problem.FieldError(ex.getName(), INVALID_FORMAT)),
                                                "VALIDATION_FAILED", null));
        }

        /**
         * Type-conversion errors carry Spring's technical text (including the rejected value), so they are
         * replaced by a neutral message; Bean Validation messages are passed through unchanged.
         */
        private ResponseEntity<Problem> bindingProblem(BindingResult bindingResult) {
                List<Problem.FieldError> errors = bindingResult.getFieldErrors().stream()
                                .map(fe -> new Problem.FieldError(fe.getField(),
                                                "typeMismatch".equals(fe.getCode()) ? INVALID_FORMAT : fe.getDefaultMessage()))
                                .toList();
                log.warn("validation_failed fields={}", errors.stream().map(Problem.FieldError::field).toList());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                                .body(Problem.of(400, "Validation Failed", "One or more fields are invalid.", errors,
                                                "VALIDATION_FAILED", null));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Problem> handleUnreadableRequest(HttpMessageNotReadableException ex) {
                log.warn("request_failure code=VALIDATION_FAILED status=400");
                return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(
                                Problem.of(400, "Validation Failed", "The request body is invalid.",
                                                "VALIDATION_FAILED"));
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<Problem> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
                log.warn("request_failure code=UNSUPPORTED_MEDIA_TYPE status=415");
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).cacheControl(CacheControl.noStore())
                                .body(
                                                Problem.of(415, "Unsupported Media Type",
                                                                "Use application/json for this request.",
                                                                "UNSUPPORTED_MEDIA_TYPE"));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Problem> handleIllegalArgument(IllegalArgumentException ex) {
                log.warn("bad_request detail={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Problem.of(400, "Bad Request", ex.getMessage()));
        }
}
