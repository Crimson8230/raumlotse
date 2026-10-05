package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class MigrationVersionTest {
    @Test
    void bookingConfirmationMigrationImmediatelyFollowsVersionEleven() throws Exception {
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");
        List<String> orderedNames = java.util.Arrays.stream(resources)
                .map(resource -> resource.getFilename())
                .sorted((left, right) -> versionOf(left).compareTo(versionOf(right)))
                .toList();

        assertThat(orderedNames).containsSubsequence(
                "V11__create_room_device_control.sql",
                "V12__create_booking_confirmation.sql");
    }

    @Test
    void packagedMigrationsHaveUniqueFlywayVersions() throws Exception {
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/V*__*.sql");
        assertThat(resources).isNotEmpty();
        Map<MigrationVersion, String> seen = new HashMap<>();
        var pattern = Pattern.compile("V(.+)__.+\\.sql");
        for (var resource : resources) {
            var name = resource.getFilename();
            var match = pattern.matcher(name);
            assertThat(match.matches()).as("Valid migration filename: %s", name).isTrue();
            var version = MigrationVersion.fromVersion(match.group(1));
            assertThat(seen.putIfAbsent(version, name))
                    .as("Duplicate Flyway version %s: %s", version, name).isNull();
        }
    }

    private static MigrationVersion versionOf(String filename) {
        var match = Pattern.compile("V(.+)__.+\\.sql").matcher(filename);
        assertThat(match.matches()).as("Valid migration filename: %s", filename).isTrue();
        return MigrationVersion.fromVersion(match.group(1));
    }
}
