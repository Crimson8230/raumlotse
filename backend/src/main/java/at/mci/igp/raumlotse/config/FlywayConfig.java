package at.mci.igp.raumlotse.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4 no longer auto-configures Flyway (no {@code FlywayAutoConfiguration} exists on
 * the classpath as of 4.1.1, unlike earlier Boot versions) — migrations must be triggered
 * manually. This bean's factory method runs {@link Flyway#migrate()} as a side effect during
 * context refresh, which completes before the embedded web server starts accepting requests,
 * so the schema is always in place before the first HTTP request is served.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
        return flyway;
    }
}
