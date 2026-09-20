package at.mci.igp.raumlotse.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class LoginRequest {
    @NotNull @Size(max = 1024)
    private String email;
    @NotNull @Size(min = 1, max = 1024)
    private String password;

    @JsonProperty public String getEmail() { return email; }
    @JsonProperty public String getPassword() { return password; }
    @JsonProperty public void setEmail(String email) { this.email = email; }
    @JsonProperty public void setPassword(String password) { this.password = password; }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unexpected login request field");
    }
}
