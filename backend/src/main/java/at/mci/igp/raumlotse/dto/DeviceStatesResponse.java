package at.mci.igp.raumlotse.dto;

/** Simulated states of the automated room devices; {@code door} is "LOCKED" or "UNLOCKED" (feature 014). */
public record DeviceStatesResponse(boolean lighting, boolean ventilation, String door) {

    public static String doorState(boolean unlocked) {
        return unlocked ? "UNLOCKED" : "LOCKED";
    }
}
