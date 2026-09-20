package at.mci.igp.raumlotse.dto;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Problem(String title, int status, String detail, List<FieldError> errors,
        String code, Integer retryAfterSeconds) {

    public record FieldError(String field, String message) {
    }

    public static Problem of(int status, String title, String detail) {
        return new Problem(title, status, detail, List.of(), null, null);
    }

    public static Problem of(int status, String title, String detail, List<FieldError> errors) {
        return new Problem(title, status, detail, errors, null, null);
    }

    public static Problem of(int status, String title, String detail, List<FieldError> errors,
            String code, Integer retryAfterSeconds) {
        return new Problem(title, status, detail, errors, code, retryAfterSeconds);
    }

    public static Problem of(int status, String title, String detail, String code) {
        return new Problem(title, status, detail, List.of(), code, null);
    }

    public static Problem of(int status, String title, String detail, String code, Integer retryAfterSeconds) {
        return new Problem(title, status, detail, List.of(), code, retryAfterSeconds);
    }
}
