package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.AccountAuthenticationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationLoggingIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder encoder;
    @MockitoBean AccountAuthenticationService authentication;

    @Test
    void realLoginRequestsLogSafeOutcomesForSuccessRejectionValidationCooldownAndOutage(CapturedOutput output) throws Exception {
        String email = "known-privacy-sentinel@example.test";
        String password = "password-privacy-sentinel";
        String hashSentinel = encoder.encode(password);
        var known = accounts.saveAndFlush(new UserAccount(email, "Private Account", hashSentinel));
        when(authentication.authenticate(anyString(), anyString())).thenReturn(Optional.empty());
        when(authentication.authenticate(eq(email), eq(password))).thenReturn(Optional.of(known));
        String unknown = "unknown-privacy-sentinel@example.test";
        String cooldown = "cooldown-privacy-sentinel@example.test";
        String outage = "outage-privacy-sentinel@example.test";
        when(authentication.authenticate(eq(outage), anyString()))
                .thenThrow(new DataAccessResourceFailureException("exception-privacy-sentinel"));
        List<String> sensitive = new ArrayList<>(List.of(email, password, hashSentinel, unknown, cooldown, outage,
                "Private Account", "header-privacy-sentinel", "exception-privacy-sentinel",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));

        Client successfulClient = bootstrap(sensitive);
        var success = login(email, password, successfulClient).andExpect(status().isOk()).andReturn();
        MockHttpSession authenticated = (MockHttpSession) success.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(authenticated)).andExpect(status().isOk());

        login(email, "wrong-password-privacy-sentinel", bootstrap(sensitive)).andExpect(status().isUnauthorized());
        sensitive.add("wrong-password-privacy-sentinel");
        login(unknown, "unknown-password-privacy-sentinel", bootstrap(sensitive)).andExpect(status().isUnauthorized());
        sensitive.add("unknown-password-privacy-sentinel");

        Client validation = bootstrap(sensitive);
        sensitive.add(validation.token());
        mvc.perform(post("/api/auth/login").session(validation.session()).header("X-CSRF-TOKEN", validation.token())
                .contentType("application/json").content("{\"email\":\"malformed-privacy-sentinel@example.test\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        sensitive.add("malformed-privacy-sentinel@example.test");

        for (int i = 0; i < 5; i++) login(cooldown, "cooldown-wrong-password", bootstrap(sensitive)).andExpect(status().isUnauthorized());
        login(cooldown, "cooldown-correct-password", bootstrap(sensitive)).andExpect(status().isTooManyRequests());
        sensitive.add("cooldown-wrong-password");
        sensitive.add("cooldown-correct-password");

        login(outage, "outage-password-privacy-sentinel", bootstrap(sensitive)).andExpect(status().isServiceUnavailable());
        sensitive.add("outage-password-privacy-sentinel");
        String logs = output.getAll();
        assertThat(logs).contains("authentication outcome=success", "authentication outcome=invalid_credentials",
                "authentication outcome=cooldown", "authentication outcome=unavailable",
                "authentication_failure code=INVALID_CREDENTIALS status=401",
                "authentication_failure code=LOGIN_COOLDOWN status=429",
                "authentication_failure code=AUTH_UNAVAILABLE status=503",
                "validation_failed")
                .doesNotContain(sensitive.toArray(String[]::new));
    }

    private Client bootstrap(List<String> sentinels) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").header("X-privacy-sentinel", "header-privacy-sentinel"))
                .andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        sentinels.add(token);
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        return new Client(session, token);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password, Client client) throws Exception {
        return mvc.perform(post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                .contentType("application/json").content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private record Client(MockHttpSession session, String token) { }
}
