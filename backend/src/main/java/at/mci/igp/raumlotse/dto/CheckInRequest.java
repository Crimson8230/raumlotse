package at.mci.igp.raumlotse.dto;

import at.mci.igp.raumlotse.domain.CheckInMethod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** On-site check-in by QR code or NFC; {@code MANUAL} is reserved for the in-app activation (feature 014). */
public record CheckInRequest(@NotNull CheckInMethod method) {

    @AssertTrue(message = "Nur QR oder NFC sind als Check-in-Methode erlaubt.")
    public boolean isOnSiteMethod() {
        return method != CheckInMethod.MANUAL;
    }
}
