package at.mci.igp.raumlotse.exception;
public class UserRoleException extends RuntimeException {
    private final int status;
    private final String code;
    public UserRoleException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int getStatus() { return status; }
    public String getCode() { return code; }
    public static UserRoleException unavailable() {
        return new UserRoleException(503,"ROLE_MANAGEMENT_UNAVAILABLE","Role management is temporarily unavailable.");
    }
}

