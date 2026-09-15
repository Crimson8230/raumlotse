package at.mci.igp.raumlotse.dto;

import java.util.List;

public record Problem(String title, int status, String detail, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }

    public static Problem of(int status, String title, String detail) {
        return new Problem(title, status, detail, List.of());
    }

    public static Problem of(int status, String title, String detail, List<FieldError> errors) {
        return new Problem(title, status, detail, errors);
    }
}
