package at.mci.igp.raumlotse.repository;

import at.mci.igp.raumlotse.domain.CheckInSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInSettingsRepository extends JpaRepository<CheckInSettings, Short> {
}
