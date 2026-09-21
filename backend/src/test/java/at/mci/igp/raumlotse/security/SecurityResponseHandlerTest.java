package at.mci.igp.raumlotse.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class SecurityResponseHandlerTest {

    @Test
    void securityFailuresEmitSafeStructuredEvents(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"privacy-log@example.test\",\"password\":\"private-password\"}"))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("authentication_failure code=AUTH_REQUIRED status=401", "authentication_failure code=CSRF_INVALID status=403")
                .doesNotContain("privacy-log@example.test", "private-password");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousBusinessReadUsesJsonAuthenticationProblem() throws Exception {
        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void anonymousBusinessWriteUsesAuthenticationProblemBeforeCsrf() throws Exception {
        mockMvc.perform(post("/api/rooms").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void missingLoginCsrfUsesJsonProblem() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    @WithMockUser
    void authenticatedBusinessCsrfFailureUsesJsonProblem() throws Exception {
        mockMvc.perform(post("/api/rooms"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
