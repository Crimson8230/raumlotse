package at.mci.igp.raumlotse.config;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginAttemptKeyConfig {
    @Bean
    byte[] loginAttemptHmacKey(@Value("${AUTH_ATTEMPT_HMAC_KEY:}") String configuredKey) {
        try {
            byte[] key = Base64.getDecoder().decode(configuredKey);
            if (key.length < 32) throw new IllegalArgumentException();
            return key;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("AUTH_ATTEMPT_HMAC_KEY must be base64 for at least 32 random bytes.");
        }
    }
}
