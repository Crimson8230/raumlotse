package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.service.LoginService;
import at.mci.igp.raumlotse.service.LoginAttemptService;
import at.mci.igp.raumlotse.service.EmailCanonicalizer;
import at.mci.igp.raumlotse.domain.UserAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest({AuthController.class, CsrfController.class})
@Import({SecurityConfig.class, LoginService.class, EmailCanonicalizer.class})
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean LoginAttemptService attempts;

    @Test
    void loginRequiresCsrfAndReturnsOnlySafeIdentity() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        var session = bootstrap.getRequest().getSession(false);
        var identity = new AuthenticatedUser(UUID.randomUUID(), "Test User");
        var account = org.mockito.Mockito.mock(UserAccount.class);
        when(account.getId()).thenReturn(identity.userId());
        when(account.getDisplayName()).thenReturn(identity.displayName());
        when(attempts.authenticate("user@example.test", " exact "))
                .thenReturn(new LoginAttemptService.Attempt(LoginAttemptService.Outcome.SUCCESS, account, 0));

        MvcResult login = mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) session)
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"user@example.test\",\"password\":\" exact \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(identity.userId().toString()))
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        var authenticatedSession = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        MvcResult fresh = mvc.perform(get("/api/auth/csrf").session(authenticatedSession))
                .andExpect(status().isOk()).andReturn();
        var freshToken = com.jayway.jsonpath.JsonPath.read(fresh.getResponse().getContentAsString(), "$.token");
        org.assertj.core.api.Assertions.assertThat(freshToken).isNotEqualTo(token);
        mvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(identity.userId().toString()));
        mvc.perform(post("/api/rooms").session(authenticatedSession).header("X-CSRF-TOKEN", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void wrongCredentialsHaveGenericSafeResponse() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        when(attempts.authenticate("nobody@example.test", "wrong"))
                .thenReturn(new LoginAttemptService.Attempt(LoginAttemptService.Outcome.INVALID_CREDENTIALS, null, 0));

        mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"nobody@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Email address or password is incorrect."));
    }

    @Test
    void fifthFailureAndActiveCooldownReturnRetryMetadata() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        when(attempts.authenticate("fifth@example.test", "wrong"))
                .thenReturn(new LoginAttemptService.Attempt(LoginAttemptService.Outcome.INVALID_CREDENTIALS, null, 900));
        mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"fifth@example.test\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.retryAfterSeconds").value(900))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "900"));

        MvcResult next = mvc.perform(get("/api/auth/csrf")).andReturn();
        var nextToken = com.jayway.jsonpath.JsonPath.read(next.getResponse().getContentAsString(), "$.token");
        when(attempts.authenticate("fifth@example.test", "right"))
                .thenReturn(new LoginAttemptService.Attempt(LoginAttemptService.Outcome.COOLDOWN, null, 899));
        mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) next.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", nextToken).contentType("application/json")
                        .content("{\"email\":\"fifth@example.test\",\"password\":\"right\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_COOLDOWN"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(899))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "899"));
    }

    @Test
    void unknownCredentialFieldsAreRejectedWithoutEchoingSubmittedValues() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"user@example.test\",\"password\":\"secret\",\"roles\":[\"ADMIN\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void invalidLoginInputsNeverReachTheAttemptLimiter() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        var session = (org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false);
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"email\":\"user@example.test\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"password\":\"secret\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(attempts);
    }

    @Test
    void authenticationStoreFailureIsSanitizedAs503() throws Exception {
        MvcResult bootstrap = mvc.perform(get("/api/auth/csrf")).andReturn();
        var token = com.jayway.jsonpath.JsonPath.read(bootstrap.getResponse().getContentAsString(), "$.token");
        when(attempts.authenticate("user@example.test", "password"))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database sentinel"));
        mvc.perform(post("/api/auth/login").session((org.springframework.mock.web.MockHttpSession) bootstrap.getRequest().getSession(false))
                        .header("X-CSRF-TOKEN", token).contentType("application/json")
                        .content("{\"email\":\"user@example.test\",\"password\":\"password\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database sentinel"))));
    }
}
