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
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(NotFoundException.class)
        public ResponseEntity<Problem> handleNotFound(NotFoundException ex) {
                log.warn("not_found detail={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ex.getCode() == null ? Problem.of(404, "Not Found", ex.getMessage())
                                                : Problem.of(404, "Not Found", ex.getMessage(), ex.getCode()));
        }

        @ExceptionHandler(UserRoleException.class)
        public ResponseEntity<Problem> handleUserRole(UserRoleException ex) {
                return ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore())
                                .body(Problem.of(ex.getStatus(), HttpStatus.valueOf(ex.getStatus()).getReasonPhrase(),
                                                ex.getMessage(), ex.getCode()));
        }

        @ExceptionHandler(ConflictException.class)
        public ResponseEntity<Problem> handleConflict(ConflictException ex) {
                log.warn("conflict detail={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Problem.of(409, "Conflict", ex.getMessage()));
        }

        @ExceptionHandler(CheckInRejectedException.class)
        public ResponseEntity<Problem> handleCheckInRejected(CheckInRejectedException ex) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Problem.of(409, "Check-in nicht möglich", ex.getMessage(), ex.getReason().name()));
        }

        @ExceptionHandler(MapRequestException.class)
        public ResponseEntity<Problem> handleMapRequest(MapRequestException ex) {
                log.warn("map_request_rejected code={} status={}", ex.getCode(), ex.getStatus().value());
                return ResponseEntity.status(ex.getStatus()).cacheControl(CacheControl.noStore())
                                .body(Problem.of(ex.getStatus().value(), ex.getStatus().getReasonPhrase(),
                                                ex.getMessage(), ex.getCode()));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<Problem> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
                log.warn("map_request_rejected code=MAP_IMAGE_TOO_LARGE status=413");
                return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).cacheControl(CacheControl.noStore())
                                .body(Problem.of(413, "Content Too Large", "Das Bild ist größer als 10 MB.",
                                                "MAP_IMAGE_TOO_LARGE"));
        }

        @ExceptionHandler(DeviceOperationException.class)
        public ResponseEntity<Problem> handleDeviceOperation(DeviceOperationException ex) {
                log.warn("device_command_failed status=503");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                .body(Problem.of(503, "Device Unavailable", "Das Gerät hat den Befehl nicht bestätigt.", "DEVICE_OPERATION_FAILED"));
        }

        @ExceptionHandler(DeviceAccessDeniedException.class)
        public ResponseEntity<Problem> handleDeviceAccessDenied(DeviceAccessDeniedException ex) {
                log.warn("device_authorization_denied status=403");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(Problem.of(403, "Forbidden", "Sie haben für diesen Raum keine aktive Reservierung.", "DEVICE_ACCESS_DENIED"));
        }

        @ExceptionHandler(OptimisticLockingFailureException.class)
        public ResponseEntity<Problem> handleOptimisticLocking(OptimisticLockingFailureException ex) {
                log.warn("stale_write detail={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Problem.of(409, "Conflict",
                                                "Der Datensatz wurde zwischenzeitlich von jemand anderem geändert. Bitte neu laden und erneut versuchen."));
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<Problem> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
                log.warn("data_integrity_violation detail={}", ex.getMostSpecificCause().getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(Problem.of(409, "Conflict",
                                                "Die Anfrage kollidiert mit einem vorhandenen Datensatz (z. B. doppelter Name)."));
        }

        private static final String INVALID_FORMAT = "hat ein ungültiges Format";

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
                                .body(Problem.of(400, "Validation Failed", "Ein oder mehrere Parameter sind ungültig.",
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
                                .body(Problem.of(400, "Validation Failed", "Ein oder mehrere Felder sind ungültig.", errors,
                                                "VALIDATION_FAILED", null));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Problem> handleUnreadableRequest(HttpMessageNotReadableException ex) {
                log.warn("request_failure code=VALIDATION_FAILED status=400");
                return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(
                                Problem.of(400, "Validation Failed", "Der Anfragetext ist ungültig.",
                                                "VALIDATION_FAILED"));
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<Problem> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
                log.warn("request_failure code=UNSUPPORTED_MEDIA_TYPE status=415");
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).cacheControl(CacheControl.noStore())
                                .body(
                                                Problem.of(415, "Unsupported Media Type",
                                                                "Bitte application/json verwenden.",
                                                                "UNSUPPORTED_MEDIA_TYPE"));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Problem> handleIllegalArgument(IllegalArgumentException ex) {
                log.warn("bad_request detail={}", ex.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Problem.of(400, "Bad Request", ex.getMessage()));
        }
}
