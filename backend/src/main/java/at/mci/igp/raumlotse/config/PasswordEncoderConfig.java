package at.mci.igp.raumlotse.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {
    public static final String ENCODING_ID = "pbkdf2-sha256-600000-v1";

    @Bean
    public PasswordEncoder passwordEncoder() {
        var pbkdf2 = new Pbkdf2PasswordEncoder("", 16, 600_000, 256);
        pbkdf2.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        pbkdf2.setEncodeHashAsBase64(false);
        return new DelegatingPasswordEncoder(ENCODING_ID, Map.of(ENCODING_ID, pbkdf2));
    }
}
