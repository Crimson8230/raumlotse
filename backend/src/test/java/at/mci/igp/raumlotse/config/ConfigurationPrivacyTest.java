package at.mci.igp.raumlotse.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigurationPrivacyTest {
    @Test
    void composeRequiresCredentialsInsteadOfCommittingDefaults() throws Exception {
        Path compose = Path.of(System.getProperty("user.dir")).getParent().resolve("docker-compose.yml");
        String yaml = Files.readString(compose);
        assertThat(yaml).contains("POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:")
                .contains("PGADMIN_DEFAULT_EMAIL: ${PGADMIN_DEFAULT_EMAIL:")
                .contains("PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_DEFAULT_PASSWORD:")
                .doesNotContain("POSTGRES_PASSWORD: raumlotse", "PGADMIN_DEFAULT_PASSWORD: admin",
                        "PGADMIN_DEFAULT_EMAIL: admin@admin.com");
    }
}
