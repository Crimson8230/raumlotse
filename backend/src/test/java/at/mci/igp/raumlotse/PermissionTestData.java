package at.mci.igp.raumlotse;

import java.util.List;
import java.util.Map;

/** Contract examples shared by permission integration tests. */
public final class PermissionTestData {
    public static final List<String> ROLES = List.of(
            "ADMIN", "UNIVERSITY_STAFF", "STUDENT", "LECTURER", "VIEWER");
    public static final List<String> PERMISSIONS = List.of(
            "READ", "RESERVE", "OWN_RESERVATION_MANAGE", "OTHER_RESERVATION_MANAGE",
            "OWN_ACTIVE_DEVICE_CONTROL", "BUILDING_MANAGE", "FLOOR_MANAGE",
            "EQUIPMENT_TYPE_MANAGE", "ROOM_MANAGE", "MAP_MANAGE",
            "ROOM_PLACEMENT_MANAGE", "CONNECTION_MANAGE", "STATISTICS_READ",
            "RESERVATION_MAINTENANCE");
    public static final Map<String, List<String>> INITIAL = Map.of(
            "VIEWER", List.of("READ"),
            "STUDENT", List.of("READ", "RESERVE", "OWN_RESERVATION_MANAGE", "OWN_ACTIVE_DEVICE_CONTROL"),
            "LECTURER", List.of("READ", "RESERVE", "OWN_RESERVATION_MANAGE", "OWN_ACTIVE_DEVICE_CONTROL"),
            "UNIVERSITY_STAFF", List.of("READ", "RESERVE", "OWN_RESERVATION_MANAGE", "OWN_ACTIVE_DEVICE_CONTROL"),
            "ADMIN", PERMISSIONS);

    private PermissionTestData() {
    }
}
