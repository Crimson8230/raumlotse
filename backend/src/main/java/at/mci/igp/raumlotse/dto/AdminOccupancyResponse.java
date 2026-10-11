package at.mci.igp.raumlotse.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Read-only occupancy responses (feature 016). Contain no personal data of the people who booked. */
public final class AdminOccupancyResponse {
    private AdminOccupancyResponse() {
    }

    public enum CheckInStatus { CHECKED_IN, NOT_CHECKED_IN }

    public record Snapshot(
            Instant generatedAt,
            UUID buildingId,
            Totals totals,
            List<BuildingOccupancy> buildings,
            List<BookingEntry> runningBookings) {}

    public record Totals(int checkedInPersons, int occupiedRooms) {}

    public record BuildingOccupancy(
            UUID buildingId,
            String buildingName,
            boolean buildingActive,
            int checkedInPersons,
            int occupiedRooms) {}

    public record BookingEntry(
            UUID reservationId,
            UUID roomId,
            String roomName,
            UUID buildingId,
            String buildingName,
            UUID floorId,
            String floorName,
            Instant startTime,
            Instant endTime,
            int expectedAttendees,
            CheckInStatus checkInStatus,
            Instant checkedInAt) {}

    public record Forecast(
            LocalDate date,
            String zone,
            Instant generatedAt,
            UUID buildingId,
            Integer peakExpectedPersons,
            List<ForecastBuilding> buildings,
            List<HourForecast> hours) {}

    public record ForecastBuilding(
            UUID buildingId,
            String buildingName,
            boolean buildingActive,
            Integer peakExpectedPersons) {}

    public record HourForecast(
            Instant start,
            Instant end,
            String localLabel,
            boolean past,
            Integer expectedPersons,
            Integer bookedRooms,
            boolean peak,
            List<HourBuildingValue> buildings,
            List<FloorGroup> floors) {}

    public record HourBuildingValue(UUID buildingId, Integer expectedPersons, Integer bookedRooms, boolean peak) {}

    public record FloorGroup(
            UUID buildingId,
            String buildingName,
            UUID floorId,
            String floorName,
            int expectedPersons,
            List<BookingEntry> bookings) {}
}
