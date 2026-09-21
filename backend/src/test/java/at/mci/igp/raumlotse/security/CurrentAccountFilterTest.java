package at.mci.igp.raumlotse.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.repository.UserAccountRepository;
import at.mci.igp.raumlotse.service.CurrentAccountFilter;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
class CurrentAccountFilterTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test
    void missingPersistedAccountClearsContextBeforeContinuingToSecurityChecks(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var repository = mock(UserAccountRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "old name"), null, java.util.List.of()));
        var filter = new CurrentAccountFilter(repository, new JsonMapper());
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(output.getAll()).contains("authentication_failure code=AUTH_REQUIRED reason=account_missing status=401");
    }

    @Test
    void accountStoreFailureReturnsSafe503WithoutCallingBusinessChain(
            org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var repository = mock(UserAccountRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenThrow(new DataAccessResourceFailureException("private database detail"));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id, "User"), null, java.util.List.of()));
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        new CurrentAccountFilter(repository, new JsonMapper()).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("AUTH_UNAVAILABLE").doesNotContain("private database detail");
        assertThat(chain.getRequest()).isNull();
        assertThat(output.getAll()).contains("authentication_failure code=AUTH_UNAVAILABLE reason=account_lookup status=503")
                .doesNotContain("private database detail");
    }
}
