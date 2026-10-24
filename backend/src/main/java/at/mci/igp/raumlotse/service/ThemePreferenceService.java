package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.dto.ThemePreferenceResponse;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ThemePreferenceService {
    private final UserAccountRepository accounts;

    public ThemePreferenceService(UserAccountRepository accounts) { this.accounts = accounts; }

    public ThemePreferenceResponse read(UUID userId) { return response(account(userId)); }

    @Transactional
    public ThemePreferenceResponse choose(UUID userId, String theme) {
        if (!"LIGHT".equals(theme) && !"DARK".equals(theme)) {
            throw new IllegalArgumentException("Ungültiges Farbschema.");
        }
        UserAccount account = lockedAccount(userId);
        account.selectTheme(theme);
        accounts.saveAndFlush(account);
        return response(account);
    }

    @Transactional
    public ThemePreferenceResponse skip(UUID userId) {
        UserAccount account = lockedAccount(userId);
        if (!account.isThemeOnboardingCompleted()) {
            account.skipThemeOnboarding();
            accounts.saveAndFlush(account);
        }
        return response(account);
    }

    private UserAccount account(UUID userId) {
        return accounts.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private UserAccount lockedAccount(UUID userId) {
        return accounts.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static ThemePreferenceResponse response(UserAccount account) {
        String selected = account.getThemeCode();
        if (!"LIGHT".equals(selected) && !"DARK".equals(selected)) selected = null;
        return new ThemePreferenceResponse(selected == null ? "LIGHT" : selected,
                selected, !account.isThemeOnboardingCompleted());
    }
}
