package at.mci.igp.raumlotse.service;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class EmailCanonicalizer {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public String canonicalize(String email) {
        if (email == null) throw new IllegalArgumentException("Invalid email");
        String canonical = email.strip().toLowerCase(Locale.ROOT);
        if (canonical.isEmpty() || canonical.length() > 254 || !EMAIL.matcher(canonical).matches()) {
            throw new IllegalArgumentException("Invalid email");
        }
        return canonical;
    }
}
