package at.mci.igp.raumlotse.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.repository.StatisticsRepository;
import at.mci.igp.raumlotse.exception.GlobalExceptionHandler;
import at.mci.igp.raumlotse.service.AdminStatisticsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminStatisticsControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminStatisticsController(
            new AdminStatisticsService(new StatisticsRepository())))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void rejectsInvalidDateRange() throws Exception {
        mvc.perform(get("/api/admin/statistics")
                        .param("from", "2026-02-02")
                        .param("to", "2026-02-01"))
                .andExpect(status().isBadRequest());
    }
}
