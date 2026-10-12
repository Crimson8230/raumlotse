package at.mci.igp.raumlotse.exception;

public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
