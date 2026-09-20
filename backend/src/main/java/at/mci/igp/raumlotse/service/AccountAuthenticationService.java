package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.exception.AuthenticationUnavailableException;
import at.mci.igp.raumlotse.config.PasswordEncoderConfig;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountAuthenticationService {
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final EmailCanonicalizer canonicalizer;
    private final String dummyHash;

    public AccountAuthenticationService(UserAccountRepository accounts, PasswordEncoder passwordEncoder,
            EmailCanonicalizer canonicalizer) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.canonicalizer = canonicalizer;
        byte[] dummy = new byte[32];
        new SecureRandom().nextBytes(dummy);
        this.dummyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(dummy));
    }

    public Optional<UserAccount> authenticate(String email, String password) {
        String canonical = canonicalizer.canonicalize(email);
        Optional<UserAccount> account = accounts.findByEmail(canonical);
        if (account.isEmpty()) {
            try {
                passwordEncoder.matches(password, dummyHash);
            } catch (RuntimeException ex) {
                throw new AuthenticationUnavailableException();
            }
            return Optional.empty();
        }
        String encoded = account.get().getPasswordHash();
        if (encoded == null || !encoded.startsWith("{" + PasswordEncoderConfig.ENCODING_ID + "}")) {
            throw new AuthenticationUnavailableException();
        }
        try {
            return passwordEncoder.matches(password, encoded) ? account : Optional.empty();
        } catch (RuntimeException ex) {
            throw new AuthenticationUnavailableException();
        }
    }
}
