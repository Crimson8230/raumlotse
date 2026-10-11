package at.mci.igp.raumlotse.service;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.dto.AdminOccupancyResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.OccupancyRepository;
import at.mci.igp.raumlotse.repository.OccupancyRepository.BookingRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Current occupancy and day forecast for administrators (feature 016). "Checked-in persons" are the expected
 * attendees of covering ACTIVE bookings; the system records check-ins per booking, not per person.
 */
@Service
@Transactional(readOnly = true)
public class AdminOccupancyService {
    private static final ZoneId ZONE = AdminStatisticsPeriod.ZONE;
    private static final int FIRST_DAYTIME_HOUR = 7;
    private static final int LAST_DAYTIME_HOUR = 21;
    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("HH:mm");
    private static final Set<ReservationStatus> PLANNED = Set.of(ReservationStatus.RESERVED, ReservationStatus.ACTIVE);
    private final OccupancyRepository repository;
    private final Clock clock;

    public AdminOccupancyService(OccupancyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public AdminOccupancyResponse.Snapshot snapshot(UUID buildingIdOrNull) {
        requireBuilding(buildingIdOrNull);
        Instant now = clock.instant();
        List<BookingRow> covering = repository.findCovering(now, buildingIdOrNull);
        List<BookingRow> checkedIn = covering.stream().filter(row -> row.status() == ReservationStatus.ACTIVE).toList();
        List<AdminOccupancyResponse.BookingEntry> running = covering.stream()
                .sorted(Comparator.comparing(BookingRow::buildingName).thenComparing(BookingRow::roomName)
                        .thenComparing(BookingRow::startTime))
                .map(AdminOccupancyService::entry)
                .toList();
        return new AdminOccupancyResponse.Snapshot(now, buildingIdOrNull, totals(checkedIn),
                buildingOccupancy(checkedIn, buildingIdOrNull), running);
    }

    /** Hourly plan values for {@code date} from RESERVED and ACTIVE bookings, per building and in total. */
    public AdminOccupancyResponse.Forecast forecast(LocalDate date, UUID buildingIdOrNull) {
        requireBuilding(buildingIdOrNull);
        Instant now = clock.instant();
        if (date.isBefore(LocalDate.ofInstant(now, ZONE))) {
            throw new IllegalArgumentException("Vergangene Tage sind im Bereich Statistiken auswertbar.");
        }
        Instant dayStart = date.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        List<BookingRow> rows = repository.findOverlapping(dayStart, dayEnd, buildingIdOrNull).stream()
                .filter(row -> PLANNED.contains(row.status()))
                .toList();
        List<OccupancyRepository.BuildingRow> columns = forecastBuildings(rows, buildingIdOrNull);

        List<HourValues> hours = new ArrayList<>();
        // Stepping on the instant timeline gives 23 or 25 hours on DST change days.
        for (ZonedDateTime hour = date.atStartOfDay(ZONE); hour.toInstant().isBefore(dayEnd); hour = hour.plusHours(1)) {
            Instant start = hour.toInstant();
            Instant end = hour.plusHours(1).toInstant();
            List<BookingRow> hourRows = rows.stream().filter(row -> overlaps(row, start, end)).toList();
            boolean daytime = hour.getHour() >= FIRST_DAYTIME_HOUR && hour.getHour() <= LAST_DAYTIME_HOUR;
            if (daytime || !hourRows.isEmpty()) {
                hours.add(new HourValues(start, end, hour.format(HOUR_LABEL), !end.isAfter(now), hourRows, columns));
            }
        }

        Integer totalPeak = peak(hours.stream().filter(h -> !h.past).map(h -> h.total.persons).toList());
        Map<UUID, Integer> buildingPeaks = new LinkedHashMap<>();
        for (OccupancyRepository.BuildingRow column : columns) {
            buildingPeaks.put(column.id(), peak(hours.stream().filter(h -> !h.past)
                    .map(h -> h.perBuilding.get(column.id()).persons).toList()));
        }

        List<AdminOccupancyResponse.HourForecast> hourForecasts = hours.stream()
                .map(h -> h.toResponse(columns, totalPeak, buildingPeaks))
                .toList();
        List<AdminOccupancyResponse.ForecastBuilding> buildings = columns.stream()
                .map(column -> new AdminOccupancyResponse.ForecastBuilding(column.id(), column.name(),
                        column.status() == EntityStatus.ACTIVE, buildingPeaks.get(column.id())))
                .toList();
        return new AdminOccupancyResponse.Forecast(date, ZONE.getId(), now, buildingIdOrNull, totalPeak, buildings,
                hourForecasts);
    }

    /**
     * Per-building totals: every active building (zeros included) and inactive buildings that currently have values,
     * so that the list adds up to the totals (FR-009). With a filter, only that building.
     */
    private List<AdminOccupancyResponse.BuildingOccupancy> buildingOccupancy(List<BookingRow> checkedIn,
            UUID buildingIdOrNull) {
        Map<UUID, List<BookingRow>> byBuilding = checkedIn.stream().collect(Collectors.groupingBy(BookingRow::buildingId));
        return repository.findBuildings().stream()
                .filter(b -> buildingIdOrNull == null
                        ? b.status() == EntityStatus.ACTIVE || byBuilding.containsKey(b.id())
                        : b.id().equals(buildingIdOrNull))
                .sorted(Comparator.comparing(OccupancyRepository.BuildingRow::name))
                .map(b -> {
                    AdminOccupancyResponse.Totals values = totals(byBuilding.getOrDefault(b.id(), List.of()));
                    return new AdminOccupancyResponse.BuildingOccupancy(b.id(), b.name(), b.status() == EntityStatus.ACTIVE,
                            values.checkedInPersons(), values.occupiedRooms());
                })
                .toList();
    }

    /** Active buildings, plus inactive ones with bookings that day; only the filtered building when one is set. */
    private List<OccupancyRepository.BuildingRow> forecastBuildings(List<BookingRow> rows, UUID buildingIdOrNull) {
        Set<UUID> withBookings = rows.stream().map(BookingRow::buildingId).collect(Collectors.toSet());
        return repository.findBuildings().stream()
                .filter(b -> buildingIdOrNull == null
                        ? b.status() == EntityStatus.ACTIVE || withBookings.contains(b.id())
                        : b.id().equals(buildingIdOrNull))
                .sorted(Comparator.comparing(OccupancyRepository.BuildingRow::name))
                .toList();
    }

    private static boolean overlaps(BookingRow row, Instant from, Instant to) {
        return row.effectiveStart().isBefore(to) && row.endTime().isAfter(from);
    }

    /** The day's maximum, or null when it is 0 (no peak to mark). */
    private static Integer peak(List<Integer> values) {
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(0);
        return max > 0 ? max : null;
    }

    /**
     * Highest sum of expected attendees booked at the same moment within {@code [from, to)}. Ends are processed
     * before starts at the same instant, so back-to-back bookings are not added up (half-open intervals).
     */
    static int maxSimultaneous(List<BookingRow> rows, Instant from, Instant to) {
        record Change(Instant at, int delta) {}
        List<Change> changes = new ArrayList<>();
        for (BookingRow row : rows) {
            Instant start = row.effectiveStart().isAfter(from) ? row.effectiveStart() : from;
            Instant end = row.endTime().isBefore(to) ? row.endTime() : to;
            if (end.isAfter(start)) {
                changes.add(new Change(start, row.expectedAttendees()));
                changes.add(new Change(end, -row.expectedAttendees()));
            }
        }
        changes.sort(Comparator.comparing(Change::at).thenComparingInt(Change::delta));
        int current = 0;
        int max = 0;
        for (Change change : changes) {
            current += change.delta();
            max = Math.max(max, current);
        }
        return max;
    }

    private record Value(int persons, int rooms) {
        static Value of(List<BookingRow> rows, Instant from, Instant to) {
            return new Value(maxSimultaneous(rows, from, to), (int) rows.stream().map(BookingRow::roomId).distinct().count());
        }
    }

    private static final class HourValues {
        private final Instant start;
        private final Instant end;
        private final String label;
        private final boolean past;
        private final List<BookingRow> rows;
        private final Value total;
        private final Map<UUID, Value> perBuilding = new LinkedHashMap<>();

        private HourValues(Instant start, Instant end, String label, boolean past, List<BookingRow> rows,
                List<OccupancyRepository.BuildingRow> columns) {
            this.start = start;
            this.end = end;
            this.label = label;
            this.past = past;
            this.rows = rows;
            this.total = Value.of(rows, start, end);
            for (OccupancyRepository.BuildingRow column : columns) {
                perBuilding.put(column.id(), Value.of(
                        rows.stream().filter(row -> row.buildingId().equals(column.id())).toList(), start, end));
            }
        }

        private AdminOccupancyResponse.HourForecast toResponse(List<OccupancyRepository.BuildingRow> columns,
                Integer totalPeak, Map<UUID, Integer> buildingPeaks) {
            List<AdminOccupancyResponse.HourBuildingValue> buildings = columns.stream().map(column -> {
                Value value = perBuilding.get(column.id());
                Integer columnPeak = buildingPeaks.get(column.id());
                return past
                        ? new AdminOccupancyResponse.HourBuildingValue(column.id(), null, null, false)
                        : new AdminOccupancyResponse.HourBuildingValue(column.id(), value.persons, value.rooms,
                                columnPeak != null && value.persons == columnPeak);
            }).toList();
            if (past) {
                return new AdminOccupancyResponse.HourForecast(start, end, label, true, null, null, false, buildings, List.of());
            }
            return new AdminOccupancyResponse.HourForecast(start, end, label, false, total.persons, total.rooms,
                    totalPeak != null && total.persons == totalPeak, buildings, floorGroups());
        }

        /** Bookings of the hour grouped by floor, most expected persons first (FR-016). */
        private List<AdminOccupancyResponse.FloorGroup> floorGroups() {
            Map<UUID, List<BookingRow>> byFloor = rows.stream()
                    .collect(Collectors.groupingBy(BookingRow::floorId, LinkedHashMap::new, Collectors.toList()));
            return byFloor.values().stream().map(floorRows -> {
                BookingRow first = floorRows.get(0);
                List<AdminOccupancyResponse.BookingEntry> bookings = floorRows.stream()
                        .sorted(Comparator.comparingInt(BookingRow::expectedAttendees).reversed()
                                .thenComparing(BookingRow::roomName))
                        .map(AdminOccupancyService::entry)
                        .toList();
                return new AdminOccupancyResponse.FloorGroup(first.buildingId(), first.buildingName(), first.floorId(),
                        first.floorName(), maxSimultaneous(floorRows, start, end), bookings);
            }).sorted(Comparator.comparingInt(AdminOccupancyResponse.FloorGroup::expectedPersons).reversed()
                    .thenComparing(AdminOccupancyResponse.FloorGroup::buildingName)
                    .thenComparing(AdminOccupancyResponse.FloorGroup::floorName))
                    .toList();
        }
    }

    private static AdminOccupancyResponse.BookingEntry entry(BookingRow row) {
        boolean checkedIn = row.status() == ReservationStatus.ACTIVE;
        return new AdminOccupancyResponse.BookingEntry(row.reservationId(), row.roomId(), row.roomName(), row.buildingId(),
                row.buildingName(), row.floorId(), row.floorName(), row.startTime(), row.endTime(), row.expectedAttendees(),
                checkedIn ? AdminOccupancyResponse.CheckInStatus.CHECKED_IN : AdminOccupancyResponse.CheckInStatus.NOT_CHECKED_IN,
                checkedIn ? row.checkedInAt() : null);
    }

    private void requireBuilding(UUID buildingIdOrNull) {
        if (buildingIdOrNull != null && !repository.buildingExists(buildingIdOrNull)) {
            throw new NotFoundException("Gebäude " + buildingIdOrNull + " nicht gefunden.");
        }
    }

    private static AdminOccupancyResponse.Totals totals(List<BookingRow> checkedIn) {
        int persons = checkedIn.stream().mapToInt(BookingRow::expectedAttendees).sum();
        int rooms = (int) checkedIn.stream().map(BookingRow::roomId).distinct().count();
        return new AdminOccupancyResponse.Totals(persons, rooms);
    }
}
