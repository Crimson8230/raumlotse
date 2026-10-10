package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.CheckInSettings;
import at.mci.igp.raumlotse.dto.CheckInSettingsResponse;
import at.mci.igp.raumlotse.dto.CheckInSettingsUpdateRequest;
import at.mci.igp.raumlotse.repository.CheckInSettingsRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-editable check-in times (feature 014, FR-022). Read on every check-in decision and every expiry sweep, so a
 * change applies immediately.
 */
@Service
@Transactional
public class CheckInSettingsService {
    private static final Logger log = LoggerFactory.getLogger(CheckInSettingsService.class);

    private final CheckInSettingsRepository repository;
    private final Clock clock;

    public CheckInSettingsService(CheckInSettingsRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CheckInPolicy current() {
        return repository.findById(CheckInSettings.SINGLETON_ID)
                .map(settings -> new CheckInPolicy(Duration.ofMinutes(settings.getEarlyCheckInMinutes()),
                        Duration.ofMinutes(settings.getGracePeriodMinutes())))
                .orElse(CheckInPolicy.DEFAULT);
    }

    @Transactional(readOnly = true)
    public CheckInSettingsResponse read() {
        return toResponse(settings());
    }

    public CheckInSettingsResponse update(CheckInSettingsUpdateRequest request, UUID adminUserId) {
        CheckInSettings settings = settings();
        settings.change(request.earlyCheckInMinutes(), request.gracePeriodMinutes(), adminUserId, clock.instant());
        CheckInSettings saved = repository.save(settings);
        log.info("check_in_settings_changed earlyCheckInMinutes={} gracePeriodMinutes={} by={}",
                saved.getEarlyCheckInMinutes(), saved.getGracePeriodMinutes(), adminUserId);
        return toResponse(saved);
    }

    private CheckInSettings settings() {
        return repository.findById(CheckInSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("check_in_settings row is missing (migration V15)"));
    }

    private static CheckInSettingsResponse toResponse(CheckInSettings settings) {
        return new CheckInSettingsResponse(settings.getEarlyCheckInMinutes(), settings.getGracePeriodMinutes(),
                settings.getUpdatedAt());
    }
}
