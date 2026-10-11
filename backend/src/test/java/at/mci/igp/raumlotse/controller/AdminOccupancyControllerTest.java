package at.mci.igp.raumlotse.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.exception.GlobalExceptionHandler;
import at.mci.igp.raumlotse.repository.OccupancyRepository;
import at.mci.igp.raumlotse.service.AdminOccupancyService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminOccupancyControllerTest {
    static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    static final OccupancyRepository.BookingRow ROW = new OccupancyRepository.BookingRow(UUID.randomUUID(),
            UUID.randomUUID(), "R1", UUID.fromString("00000000-0000-0000-0000-00000000000a"), "A", EntityStatus.ACTIVE,
            UUID.randomUUID(), "EG", Instant.parse("2026-10-13T08:00:00Z"), Instant.parse("2026-10-13T09:00:00Z"),
            Instant.parse("2026-10-13T08:00:00Z"), at.mci.igp.raumlotse.domain.ReservationStatus.RESERVED, 4, null);

    private final OccupancyRepository repository = new OccupancyRepository() {
        @Override
        public List<BookingRow> findCovering(Instant now, UUID buildingIdOrNull) {
            return List.of(ROW);
        }

        @Override
        public List<BookingRow> findOverlapping(Instant from, Instant to, UUID buildingIdOrNull) {
            return List.of(ROW);
        }

        @Override
        public List<BuildingRow> findBuildings() {
            return List.of(new BuildingRow(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "A", EntityStatus.ACTIVE));
        }

        @Override
        public boolean buildingExists(UUID id) {
            return id.equals(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
        }
    };

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminOccupancyController(
            new AdminOccupancyService(repository, Clock.fixed(NOW, ZoneOffset.UTC))))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void returnsSnapshotWithoutCaching() throws Exception {
        mvc.perform(get("/api/admin/occupancy"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.totals.checkedInPersons").value(0))
                .andExpect(jsonPath("$.totals.occupiedRooms").value(0));
    }

    @Test
    void rejectsMalformedBuildingId() throws Exception {
        mvc.perform(get("/api/admin/occupancy").param("buildingId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBuildingIsNotFound() throws Exception {
        mvc.perform(get("/api/admin/occupancy").param("buildingId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsForecastWithoutCaching() throws Exception {
        mvc.perform(get("/api/admin/occupancy/forecast").param("date", "2026-10-13"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.date").value("2026-10-13"))
                .andExpect(jsonPath("$.hours.length()").value(15));
    }

    @Test
    void forecastRequiresADate() throws Exception {
        mvc.perform(get("/api/admin/occupancy/forecast")).andExpect(status().isBadRequest());
    }

    @Test
    void forecastRejectsPastDaysWithAHintToTheStatistics() throws Exception {
        mvc.perform(get("/api/admin/occupancy/forecast").param("date", "2026-10-11"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Statistiken")));
    }

    @Test
    void forecastRejectsMalformedDate() throws Exception {
        mvc.perform(get("/api/admin/occupancy/forecast").param("date", "2026-13-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responsesContainNoPersonalData() throws Exception {
        for (String path : List.of("/api/admin/occupancy", "/api/admin/occupancy/forecast?date=2026-10-13")) {
            String body = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(body)
                    .doesNotContain("reservedFor", "createdBy", "createdByUserId", "note", "@");
        }
    }
}
