package at.mci.igp.raumlotse.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class ThemePreferenceRequest {
    @NotNull
    @Pattern(regexp = "LIGHT|DARK")
    private String theme;

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unbekanntes Feld in der Farbschema-Auswahl.");
    }
}
