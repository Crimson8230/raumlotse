package at.mci.igp.raumlotse;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    static {
        // Keep the singleton database alive for Spring's cached ApplicationContexts.
        // JUnit's per-class @Container lifecycle stops inherited containers while a
        // cached context still holds their old connection URL.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void authenticationTestConfiguration(DynamicPropertyRegistry registry) {
        registry.add("AUTH_ATTEMPT_HMAC_KEY", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("SPRING_DATASOURCE_PASSWORD", () -> "test-only");
    }
}
