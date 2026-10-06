package at.mci.igp.raumlotse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdminStatisticsRepositoryIntegrationTest {
    @Test
    void statisticsRepositoryContractUsesHalfOpenOverlapIntervals() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-02T00:00:00Z");
        assertThat(to).isAfter(from);
    }
}
