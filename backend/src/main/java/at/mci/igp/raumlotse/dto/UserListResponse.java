package at.mci.igp.raumlotse.dto;
import java.util.List;
public record UserListResponse(List<UserSummary> items, int page, int size, long totalElements) {}

