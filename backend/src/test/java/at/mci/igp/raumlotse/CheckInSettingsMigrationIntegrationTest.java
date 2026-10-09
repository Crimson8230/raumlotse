package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** V15 (feature 014, FR-022): one row of admin-editable check-in times with the former constants as defaults. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CheckInSettingsMigrationIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void defaultsAreTheFormerConstants() {
        Map<String, Object> row = jdbc.queryForMap("select * from check_in_settings");
        assertThat(row).containsEntry("id", 1)
                .containsEntry("early_check_in_minutes", 10)
                .containsEntry("grace_period_minutes", 5)
                .containsEntry("updated_by_user_id", null);
        assertThat(row.get("updated_at")).isNotNull();
    }

    @Test
    void thereIsOnlyEverOneRowWithValuesInRange() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into check_in_settings(id, early_check_in_minutes, grace_period_minutes) values (2, 10, 5)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update check_in_settings set early_check_in_minutes = 61"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update check_in_settings set early_check_in_minutes = -1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update check_in_settings set grace_period_minutes = 0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update check_in_settings set grace_period_minutes = 31"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
