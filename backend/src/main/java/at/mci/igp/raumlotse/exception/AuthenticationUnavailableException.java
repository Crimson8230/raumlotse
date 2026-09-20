package at.mci.igp.raumlotse.exception;

public final class AuthenticationUnavailableException extends RuntimeException {
    public AuthenticationUnavailableException() { super("Authentication is temporarily unavailable."); }
}
