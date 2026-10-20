package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import at.mci.igp.raumlotse.service.ThemePreferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ExtendWith(OutputCaptureExtension.class)
class ThemePreferenceIntegrationTest extends MapIntegrationSupport {
    private static final String PATH = "/api/me/theme-preference";
    @MockitoSpyBean
    private ThemePreferenceService preferences;

    @Test
    void chooseAndReadAcrossSessions() throws Exception {
        mvc.perform(get(PATH).with(actor(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTheme").value("LIGHT"))
                .andExpect(jsonPath("$.selectedTheme").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.onboardingPending").value(true));
        mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTheme").value("DARK"))
                .andExpect(jsonPath("$.onboardingPending").value(false));
        mvc.perform(get(PATH).with(actor(user))).andExpect(jsonPath("$.selectedTheme").value("DARK"));
        mvc.perform(get(PATH).with(actor(admin))).andExpect(jsonPath("$.effectiveTheme").value("LIGHT"));
    }

    @Test
    void skipIsIdempotentAndNeverClearsSelection() throws Exception {
        mvc.perform(post(PATH + "/skip").with(actor(user)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onboardingPending").value(false));
        mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(PATH + "/skip").with(actor(user)).with(csrf()))
                .andExpect(jsonPath("$.selectedTheme").value("DARK"));
    }

    @Test
    void invalidInputAndMissingCsrfCannotChangeState() throws Exception {
        for (String body : new String[] {"{}", "{\"theme\":null}", "{\"theme\":\"BLUE\"}",
                "{\"theme\":\"LIGHT\",\"extra\":true}"}) {
            mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(put(PATH).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"DARK\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH).with(actor(user))).andExpect(jsonPath("$.onboardingPending").value(true));
    }

    @Test
    void anonymousRequestsAreRejectedAndUnknownStoredThemeFallsBack() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/skip").with(csrf())).andExpect(status().isUnauthorized());
        db.update("update user_account set theme_code='REMOVED' where id=?", user);
        mvc.perform(get(PATH).with(actor(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveTheme").value("LIGHT"))
                .andExpect(jsonPath("$.selectedTheme").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void latestSuccessfulChoiceIsPerAccount() throws Exception {
        UUID other = account("other-theme@example.test", "STUDENT");
        mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"DARK\"}")).andExpect(status().isOk());
        mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"LIGHT\"}")).andExpect(status().isOk());
        assertThat(db.queryForObject("select theme_code from user_account where id=?", String.class, user)).isEqualTo("LIGHT");
        assertThat(db.queryForObject("select theme_code from user_account where id=?", String.class, other)).isNull();
    }

    @Test
    void eachRoleCanUsePersonalPreferenceAndUnknownMethodsAreDenied() throws Exception {
        for (String role : new String[] {"ADMIN", "UNIVERSITY_STAFF", "STUDENT", "LECTURER", "VIEWER"}) {
            UUID actor = account(role.toLowerCase() + "-theme@example.test", role);
            mvc.perform(get(PATH).with(actor(actor))).andExpect(status().isOk());
            mvc.perform(put(PATH).with(actor(actor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"theme\":\"LIGHT\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post(PATH + "/skip").with(actor(actor)).with(csrf()))
                    .andExpect(status().isOk());
        }
        mvc.perform(delete(PATH).with(actor(user)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void databaseFailureReturnsUnavailableWithoutDisclosingIdentity(CapturedOutput output) throws Exception {
        doThrow(new DataAccessResourceFailureException("private database detail"))
                .when(preferences).read(user);
        mvc.perform(get(PATH).with(actor(user))).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("THEME_PREFERENCE_UNAVAILABLE"));
        assertThat(output.getOut()).doesNotContain(user.toString(), "private database detail");
    }

    @Test
    void concurrentSessionWritesAreSerialized() throws Exception {
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var light = executor.submit(() -> chooseConcurrently("LIGHT", ready, go));
            var dark = executor.submit(() -> chooseConcurrently("DARK", ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(light.get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(dark.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        String saved = db.queryForObject("select theme_code from user_account where id=?", String.class, user);
        assertThat(saved).isIn("LIGHT", "DARK");
        mvc.perform(get(PATH).with(actor(user))).andExpect(jsonPath("$.selectedTheme").value(saved));
    }

    private int chooseConcurrently(String theme, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
        return mvc.perform(put(PATH).with(actor(user)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"theme\":\"" + theme + "\"}"))
                .andReturn().getResponse().getStatus();
    }
}
