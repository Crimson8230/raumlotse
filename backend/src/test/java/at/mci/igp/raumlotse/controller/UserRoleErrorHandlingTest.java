package at.mci.igp.raumlotse.controller;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserRoleController.class)
@Import({SecurityConfig.class, at.mci.igp.raumlotse.exception.UserRoleExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class UserRoleErrorHandlingTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRoleService service;
    @MockitoBean UserRoleSafety safety;
    @Test void storageAndUnexpectedErrorsAreSanitizedAndNeverLogTheException(CapturedOutput output) throws Exception {
        var actor=authentication(UsernamePasswordAuthenticationToken.authenticated(
            new AuthenticatedUser(UUID.randomUUID(),"Verified fixture"),null,List.of()));
        String sensitive="private-person@example.test SQL select password_hash from user_account";
        when(service.read(any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException(sensitive));
        mvc.perform(get("/api/admin/users/target/roles").with(actor)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("ROLE_MANAGEMENT_UNAVAILABLE")).andExpect(header().string("Cache-Control","no-store"));
        doThrow(new IllegalStateException(sensitive)).when(service).read(any(),any());
        var response=mvc.perform(get("/api/admin/users/target/roles").with(actor)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private-person","password_hash","IllegalStateException");
        assertThat(output.getAll()).doesNotContain(sensitive);
    }
}
