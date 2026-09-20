package at.mci.igp.raumlotse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import at.mci.igp.raumlotse.domain.UserAccount;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class LoginCooldownConcurrencyTest extends AbstractIntegrationTest {
    private record SessionToken(MockHttpSession session, String token) { }
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void simultaneousFailuresSerializeAndCanonicalEmailVariantsShareTheCooldown() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        accounts.saveAndFlush(new UserAccount(email, "Concurrent User", passwordEncoder.encode("correct-password")));
        List<SessionToken> clients = new ArrayList<>();
        for (int i = 0; i < 5; i++) clients.add(bootstrap());

        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(5);
        try {
            List<java.util.concurrent.Future<Integer>> attempts = new ArrayList<>();
            for (SessionToken client : clients) {
                attempts.add(executor.submit(() -> {
                    start.await();
                    return post(email, "wrong-password", client).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var attempt : attempts) assertThat(attempt.get(30, TimeUnit.SECONDS)).isEqualTo(401);
        } finally {
            executor.shutdownNow();
        }

        SessionToken equivalentAddress = bootstrap();
        var blocked = post("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", "correct-password", equivalentAddress);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);

        SessionToken independentAddress = bootstrap();
        var independent = post(UUID.randomUUID() + "@example.test", "wrong-password", independentAddress);
        assertThat(independent.getResponse().getStatus()).isEqualTo(401);
    }

    private SessionToken bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return new SessionToken((MockHttpSession) result.getRequest().getSession(false), token);
    }

    private MvcResult post(String email, String password, SessionToken client) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login").session(client.session()).header("X-CSRF-TOKEN", client.token())
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }
}
