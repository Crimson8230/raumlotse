package at.mci.igp.raumlotse.service;

import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptIdentity {
    private final byte[] key;

    public LoginAttemptIdentity(byte[] loginAttemptHmacKey) { this.key = loginAttemptHmacKey.clone(); }

    public byte[] derive(String canonicalEmail) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(canonicalEmail.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Authentication unavailable.");
        }
    }
}
