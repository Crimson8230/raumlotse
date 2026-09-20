package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class LoginCooldownIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void registeredAndUnknownAddressesReceiveTheSameFifthFailureAndFixedCooldown() throws Exception {
        String registered = UUID.randomUUID() + "@example.test";
        String unknown = UUID.randomUUID() + "@example.test";
        accounts.saveAndFlush(new UserAccount(registered, "Cooldown User", passwordEncoder.encode("correct-password")));

        var knownFifth = failFiveTimes(registered, "wrong-password");
        var unknownFifth = failFiveTimes(unknown, "wrong-password");
        String knownDetail = com.jayway.jsonpath.JsonPath.read(knownFifth.getResponse().getContentAsString(), "$.detail");
        String unknownDetail = com.jayway.jsonpath.JsonPath.read(unknownFifth.getResponse().getContentAsString(), "$.detail");
        String knownCode = com.jayway.jsonpath.JsonPath.read(knownFifth.getResponse().getContentAsString(), "$.code");
        Integer retryAfter = com.jayway.jsonpath.JsonPath.read(knownFifth.getResponse().getContentAsString(), "$.retryAfterSeconds");
        assertThat(knownDetail).isEqualTo(unknownDetail);
        assertThat(knownCode).isEqualTo("INVALID_CREDENTIALS");
        assertThat(retryAfter).isEqualTo(900);
        assertThat(knownFifth.getResponse().getHeader("Retry-After")).isEqualTo("900");

        MockHttpSession blockedSession = (MockHttpSession) knownFifth.getRequest().getSession(false);
        postCredentials(registered, "correct-password", blockedSession, tokenFrom(blockedSession))
                .andExpect(status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("LOGIN_COOLDOWN"));
    }

    private MvcResult failFiveTimes(String email, String password) throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        MockHttpSession session = (MockHttpSession) bootstrap.getRequest().getSession(false);
        MvcResult result = null;
        for (int i = 0; i < 5; i++) {
            result = mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                            .contentType("application/json")
                            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                    .andExpect(status().isUnauthorized()).andReturn();
            session = (MockHttpSession) result.getRequest().getSession(false);
        }
        return result;
    }

    private String tokenFrom(MockHttpSession session) throws Exception {
        MvcResult fresh = mvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(fresh.getResponse().getContentAsString(), "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions postCredentials(String email, String password,
            jakarta.servlet.http.HttpSession session, String token) throws Exception {
        return mvc.perform(post("/api/auth/login").session((MockHttpSession) session).header("X-CSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
