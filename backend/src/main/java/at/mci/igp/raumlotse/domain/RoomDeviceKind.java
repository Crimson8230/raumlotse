package at.mci.igp.raumlotse.domain;

public enum RoomDeviceKind {
    LIGHTING,
    VENTILATION,
    PROJECTOR,
    /** State {@code true} means unlocked (feature 014). */
    DOOR
}
