package at.mci.igp.raumlotse.exception;

import org.springframework.http.HttpStatus;

/** A map/placement request that is syntactically valid but violates a map rule (415, 422, ...). */
public class MapRequestException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MapRequestException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
