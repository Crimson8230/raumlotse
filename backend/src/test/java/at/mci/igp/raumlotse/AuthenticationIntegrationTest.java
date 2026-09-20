package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class AuthenticationIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void csrfLoginRotatesAuthenticationStateAndRequiresFreshCsrfForBusinessWrites() throws Exception {
        UUID userId;
        String email = UUID.randomUUID() + "@example.test";
        var account = accounts.saveAndFlush(new UserAccount(email, "Integration User", passwordEncoder.encode(" Exact Pass ")));
        userId = account.getId();

        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN")).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        MockHttpSession anonymousSession = (MockHttpSession) bootstrap.getRequest().getSession(false);
        MvcResult login = mvc.perform(post("/api/auth/login").session(anonymousSession)
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\" Exact Pass \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.displayName").value("Integration User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) login.getRequest().getSession(false);
        MvcResult csrf = mvc.perform(get("/api/auth/csrf").session(authenticatedSession)).andExpect(status().isOk()).andReturn();
        String freshToken = com.jayway.jsonpath.JsonPath.read(csrf.getResponse().getContentAsString(), "$.token");
        assertThat(freshToken).isNotEqualTo(token);
        mvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(userId.toString()));
        mvc.perform(get("/api/rooms").session(authenticatedSession)).andExpect(status().isOk());
        mvc.perform(post("/api/rooms").session(authenticatedSession).header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/rooms").session(authenticatedSession).header("X-CSRF-TOKEN", freshToken)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
