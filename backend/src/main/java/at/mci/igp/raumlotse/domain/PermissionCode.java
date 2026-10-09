package at.mci.igp.raumlotse.domain;

import java.util.EnumSet;
import java.util.Set;

public enum PermissionCode {
    READ("Lesen", "Räume, Karten und eigene Reservierungen lesen"),
    RESERVE("Reservieren", "Eigene Reservierungen anlegen"),
    OWN_RESERVATION_MANAGE("Eigene Reservierungen bearbeiten", "Eigene Reservierungen ändern und abschließen"),
    OTHER_RESERVATION_MANAGE("Fremde Reservierungen bearbeiten", "Fremde Details lesen und Reservierungen ändern"),
    OWN_ACTIVE_DEVICE_CONTROL("Eigene aktive Raumgeräte steuern", "Geräte bei eigener aktiver Reservierung bedienen"),
    BUILDING_MANAGE("Gebäude verwalten", "Gebäude pflegen"),
    FLOOR_MANAGE("Stockwerke verwalten", "Stockwerke pflegen"),
    EQUIPMENT_TYPE_MANAGE("Ausstattungsarten verwalten", "Ausstattungsarten pflegen"),
    ROOM_MANAGE("Räume verwalten", "Räume pflegen"),
    MAP_MANAGE("Karten verwalten", "Karten hochladen, ersetzen und entfernen"),
    ROOM_PLACEMENT_MANAGE("Raumpositionen verwalten", "Räume auf Karten platzieren"),
    CONNECTION_MANAGE("Verbindungen verwalten", "Kartenverbindungen pflegen"),
    STATISTICS_READ("Statistiken lesen", "Verwaltungsstatistiken ansehen"),
    RESERVATION_MAINTENANCE("Reservierungswartung ausführen", "Überfällige Reservierungen manuell bearbeiten");

    private static final Set<PermissionCode> MANAGEMENT = EnumSet.of(
            BUILDING_MANAGE, FLOOR_MANAGE, EQUIPMENT_TYPE_MANAGE, ROOM_MANAGE,
            MAP_MANAGE, ROOM_PLACEMENT_MANAGE, CONNECTION_MANAGE,
            STATISTICS_READ, RESERVATION_MAINTENANCE);

    private final String label;
    private final String description;

    PermissionCode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isManagement() {
        return MANAGEMENT.contains(this);
    }
}
