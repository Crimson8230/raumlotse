package at.mci.igp.raumlotse.dto;

import java.util.List;

public record NetworkWideResponse(List<MapNetworkResponse> maps, List<ConnectionGroupResponse> connectionGroups) {
}
