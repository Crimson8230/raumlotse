package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.ReservationStatus;
import at.mci.igp.raumlotse.dto.AdminOccupancyResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.OccupancyRepository;
import at.mci.igp.raumlotse.repository.OccupancyRepository.BookingRow;
import at.mci.igp.raumlotse.repository.OccupancyRepository.BuildingRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminOccupancyServiceTest {
    static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    static final UUID BUILDING_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID BUILDING_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID FLOOR_A_EG = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    static final UUID FLOOR_A_OG = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID FLOOR_B_EG = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    static final UUID ROOM_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID ROOM_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID ROOM_3 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    /** In-memory repository; the covering/overlap filtering itself is tested in OccupancyRepositoryIntegrationTest. */
    static class FakeRepository extends OccupancyRepository {
        final List<BookingRow> covering = new ArrayList<>();
        final List<BookingRow> overlapping = new ArrayList<>();
        final List<BuildingRow> buildings = new ArrayList<>(List.of(
                new BuildingRow(BUILDING_A, "A", EntityStatus.ACTIVE),
                new BuildingRow(BUILDING_B, "B", EntityStatus.ACTIVE)));

        @Override
        public List<BookingRow> findCovering(Instant now, UUID buildingIdOrNull) {
            return covering.stream().filter(r -> buildingIdOrNull == null || r.buildingId().equals(buildingIdOrNull)).toList();
        }

        @Override
        public List<BookingRow> findOverlapping(Instant from, Instant to, UUID buildingIdOrNull) {
            return overlapping.stream()
                    .filter(r -> buildingIdOrNull == null || r.buildingId().equals(buildingIdOrNull))
                    .filter(r -> r.effectiveStart().isBefore(to) && r.endTime().isAfter(from))
                    .toList();
        }

        @Override
        public List<BuildingRow> findBuildings() {
            return buildings;
        }

        @Override
        public boolean buildingExists(UUID id) {
            return buildings.stream().anyMatch(b -> b.id().equals(id));
        }
    }

    static BookingRow row(UUID room, ReservationStatus status, int attendees, Instant start, Instant end) {
        String roomName = room.equals(ROOM_1) ? "R1" : room.equals(ROOM_2) ? "R2" : "R3";
        UUID building = room.equals(ROOM_3) ? BUILDING_B : BUILDING_A;
        UUID floor = room.equals(ROOM_1) ? FLOOR_A_EG : room.equals(ROOM_2) ? FLOOR_A_OG : FLOOR_B_EG;
        String floorName = room.equals(ROOM_2) ? "1. OG" : "EG";
        Instant checkedIn = status == ReservationStatus.ACTIVE ? start : null;
        return new BookingRow(UUID.randomUUID(), room, roomName, building, building.equals(BUILDING_A) ? "A" : "B",
                EntityStatus.ACTIVE, floor, floorName, start, end, start, status, attendees, checkedIn);
    }

    static BookingRow active(UUID room, int attendees) {
        return row(room, ReservationStatus.ACTIVE, attendees, NOW.minusSeconds(1800), NOW.plusSeconds(1800));
    }

    static BookingRow reserved(UUID room, int attendees) {
        return row(room, ReservationStatus.RESERVED, attendees, NOW.minusSeconds(300), NOW.plusSeconds(1800));
    }

    static AdminOccupancyService service(FakeRepository repository, Instant now) {
        return new AdminOccupancyService(repository, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void snapshotCountsOnlyCheckedInBookings() {
        FakeRepository repository = new FakeRepository();
        repository.covering.addAll(List.of(active(ROOM_1, 4), active(ROOM_3, 10), reserved(ROOM_2, 6)));

        AdminOccupancyResponse.Snapshot snapshot = service(repository, NOW).snapshot(null);

        assertThat(snapshot.totals().checkedInPersons()).isEqualTo(14);
        assertThat(snapshot.totals().occupiedRooms()).isEqualTo(2);
        assertThat(snapshot.generatedAt()).isEqualTo(NOW);
        assertThat(snapshot.buildingId()).isNull();
    }

    @Test
    void roomWithTwoCheckedInBookingsCountsOnceButBothPersonsCount() {
        FakeRepository repository = new FakeRepository();
        repository.covering.addAll(List.of(active(ROOM_1, 4), active(ROOM_1, 3)));

        AdminOccupancyResponse.Totals totals = service(repository, NOW).snapshot(null).totals();

        assertThat(totals.checkedInPersons()).isEqualTo(7);
        assertThat(totals.occupiedRooms()).isEqualTo(1);
    }

    @Test
    void emptySnapshotHasZeroTotals() {
        AdminOccupancyResponse.Totals totals = service(new FakeRepository(), NOW).snapshot(null).totals();

        assertThat(totals.checkedInPersons()).isZero();
        assertThat(totals.occupiedRooms()).isZero();
    }

    @Test
    void unknownBuildingIsRejected() {
        assertThatThrownBy(() -> service(new FakeRepository(), NOW).snapshot(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- Day forecast (US2) ----

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    static final UUID BUILDING_C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    static Instant local(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(BERLIN).toInstant();
    }

    static BookingRow at(UUID room, ReservationStatus status, int attendees, int fromH, int fromM, int toH, int toM) {
        return row(room, status, attendees, local(DAY, fromH, fromM), local(DAY, toH, toM));
    }

    /** Quickstart scenario 4: A = R1 (EG), R2 (1. OG); B = R3 (EG). */
    static FakeRepository scenario() {
        FakeRepository repository = new FakeRepository();
        repository.overlapping.addAll(List.of(
                at(ROOM_1, ReservationStatus.RESERVED, 5, 10, 0, 12, 0),
                at(ROOM_2, ReservationStatus.ACTIVE, 12, 10, 0, 12, 0),
                at(ROOM_3, ReservationStatus.RESERVED, 7, 10, 0, 11, 0),
                at(ROOM_1, ReservationStatus.RESERVED, 3, 14, 0, 15, 0),
                at(ROOM_3, ReservationStatus.CANCELLED, 20, 11, 0, 12, 0)));
        return repository;
    }

    static AdminOccupancyResponse.HourForecast hour(AdminOccupancyResponse.Forecast forecast, String label) {
        return forecast.hours().stream().filter(h -> h.localLabel().equals(label)).findFirst().orElseThrow();
    }

    static AdminOccupancyResponse.HourBuildingValue value(AdminOccupancyResponse.HourForecast hour, UUID building) {
        return hour.buildings().stream().filter(b -> b.buildingId().equals(building)).findFirst().orElseThrow();
    }

    @Test
    void forecastShowsDaytimeHoursInLocalTime() {
        AdminOccupancyResponse.Forecast forecast = service(new FakeRepository(), NOW).forecast(DAY, null);

        assertThat(forecast.hours()).extracting(AdminOccupancyResponse.HourForecast::localLabel).containsExactly(
                "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00",
                "18:00", "19:00", "20:00", "21:00");
        assertThat(forecast.hours().get(0).start()).isEqualTo(Instant.parse("2026-10-13T05:00:00Z"));
        assertThat(forecast.hours().get(0).end()).isEqualTo(Instant.parse("2026-10-13T06:00:00Z"));
        assertThat(forecast.date()).isEqualTo(DAY);
        assertThat(forecast.zone()).isEqualTo("Europe/Berlin");
        assertThat(forecast.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void forecastCountsReservedAndCheckedInBookingsPerBuildingAndInTotal() {
        AdminOccupancyResponse.Forecast forecast = service(scenario(), NOW).forecast(DAY, null);

        AdminOccupancyResponse.HourForecast ten = hour(forecast, "10:00");
        assertThat(ten.expectedPersons()).isEqualTo(24);
        assertThat(ten.bookedRooms()).isEqualTo(3);
        assertThat(ten.peak()).isTrue();
        assertThat(value(ten, BUILDING_A)).isEqualTo(new AdminOccupancyResponse.HourBuildingValue(BUILDING_A, 17, 2, true));
        assertThat(value(ten, BUILDING_B)).isEqualTo(new AdminOccupancyResponse.HourBuildingValue(BUILDING_B, 7, 1, true));

        AdminOccupancyResponse.HourForecast eleven = hour(forecast, "11:00");
        assertThat(eleven.expectedPersons()).isEqualTo(17);
        assertThat(eleven.peak()).isFalse();
        assertThat(value(eleven, BUILDING_A).peak()).isTrue();
        assertThat(value(eleven, BUILDING_B)).isEqualTo(new AdminOccupancyResponse.HourBuildingValue(BUILDING_B, 0, 0, false));

        AdminOccupancyResponse.HourForecast two = hour(forecast, "14:00");
        assertThat(two.expectedPersons()).isEqualTo(3);
        assertThat(two.bookedRooms()).isEqualTo(1);
        assertThat(hour(forecast, "09:00").expectedPersons()).isZero();

        assertThat(forecast.peakExpectedPersons()).isEqualTo(24);
        assertThat(forecast.buildings()).containsExactly(
                new AdminOccupancyResponse.ForecastBuilding(BUILDING_A, "A", true, 17),
                new AdminOccupancyResponse.ForecastBuilding(BUILDING_B, "B", true, 7));
        assertThat(ten.buildings()).extracting(AdminOccupancyResponse.HourBuildingValue::buildingId)
                .containsExactly(BUILDING_A, BUILDING_B);
    }

    @Test
    void forecastGroupsTheHoursBookingsByFloorWithMostPersonsFirst() {
        AdminOccupancyResponse.HourForecast ten = hour(service(scenario(), NOW).forecast(DAY, null), "10:00");

        assertThat(ten.floors()).extracting(AdminOccupancyResponse.FloorGroup::floorId,
                        AdminOccupancyResponse.FloorGroup::expectedPersons)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(FLOOR_A_OG, 12),
                        org.assertj.core.groups.Tuple.tuple(FLOOR_B_EG, 7),
                        org.assertj.core.groups.Tuple.tuple(FLOOR_A_EG, 5));
        AdminOccupancyResponse.FloorGroup first = ten.floors().get(0);
        assertThat(first.buildingName()).isEqualTo("A");
        assertThat(first.floorName()).isEqualTo("1. OG");
        assertThat(first.bookings()).singleElement().satisfies(entry -> {
            assertThat(entry.roomName()).isEqualTo("R2");
            assertThat(entry.expectedAttendees()).isEqualTo(12);
            assertThat(entry.checkInStatus()).isEqualTo(AdminOccupancyResponse.CheckInStatus.CHECKED_IN);
        });
        assertThat(ten.floors().get(1).bookings().get(0).checkInStatus())
                .isEqualTo(AdminOccupancyResponse.CheckInStatus.NOT_CHECKED_IN);
        assertThat(ten.floors()).flatExtracting(AdminOccupancyResponse.FloorGroup::bookings)
                .noneMatch(entry -> entry.expectedAttendees() == 20);
    }

    @Test
    void backToBackBookingsInOneHourCountTheHighestSimultaneousNumber() {
        FakeRepository repository = new FakeRepository();
        repository.overlapping.addAll(List.of(
                at(ROOM_1, ReservationStatus.RESERVED, 6, 10, 30, 11, 0),
                at(ROOM_1, ReservationStatus.RESERVED, 8, 10, 0, 10, 30)));

        AdminOccupancyResponse.HourForecast ten = hour(service(repository, NOW).forecast(DAY, null), "10:00");

        assertThat(ten.expectedPersons()).isEqualTo(8);
        assertThat(ten.bookedRooms()).isEqualTo(1);
        assertThat(value(ten, BUILDING_A).expectedPersons()).isEqualTo(8);
        assertThat(ten.floors()).singleElement().satisfies(group -> {
            assertThat(group.expectedPersons()).isEqualTo(8);
            assertThat(group.bookings()).extracting(AdminOccupancyResponse.BookingEntry::expectedAttendees)
                    .containsExactly(8, 6);
        });
    }

    @Test
    void hoursOutsideTheDaytimeAreAddedWhenTheyHaveBookings() {
        FakeRepository repository = new FakeRepository();
        repository.overlapping.add(at(ROOM_1, ReservationStatus.RESERVED, 4, 5, 0, 6, 0));

        AdminOccupancyResponse.Forecast forecast = service(repository, NOW).forecast(DAY, null);

        assertThat(forecast.hours()).extracting(AdminOccupancyResponse.HourForecast::localLabel)
                .startsWith("05:00", "07:00").doesNotContain("06:00").hasSize(16);
        assertThat(hour(forecast, "05:00").expectedPersons()).isEqualTo(4);
    }

    @Test
    void dayWithoutBookingsHasZerosAndNoPeak() {
        AdminOccupancyResponse.Forecast forecast = service(new FakeRepository(), NOW).forecast(DAY, null);

        assertThat(forecast.peakExpectedPersons()).isNull();
        assertThat(forecast.hours()).allSatisfy(h -> {
            assertThat(h.expectedPersons()).isZero();
            assertThat(h.peak()).isFalse();
            assertThat(h.floors()).isEmpty();
        });
        assertThat(forecast.buildings()).extracting(AdminOccupancyResponse.ForecastBuilding::peakExpectedPersons)
                .containsOnlyNulls();
    }

    @Test
    void pastHoursOfTodayCarryNoValues() {
        Instant halfPastTwelve = local(DAY, 12, 30);

        AdminOccupancyResponse.Forecast forecast = service(scenario(), halfPastTwelve).forecast(DAY, null);

        AdminOccupancyResponse.HourForecast eleven = hour(forecast, "11:00");
        assertThat(eleven.past()).isTrue();
        assertThat(eleven.expectedPersons()).isNull();
        assertThat(eleven.bookedRooms()).isNull();
        assertThat(eleven.peak()).isFalse();
        assertThat(eleven.floors()).isEmpty();
        assertThat(eleven.buildings()).hasSize(2).allSatisfy(b -> {
            assertThat(b.expectedPersons()).isNull();
            assertThat(b.bookedRooms()).isNull();
            assertThat(b.peak()).isFalse();
        });
        AdminOccupancyResponse.HourForecast twelve = hour(forecast, "12:00");
        assertThat(twelve.past()).isFalse();
        assertThat(hour(forecast, "14:00").peak()).isTrue();
        assertThat(forecast.peakExpectedPersons()).isEqualTo(3);
    }

    @Test
    void pastDaysAreRejectedWithAHintToTheStatistics() {
        assertThatThrownBy(() -> service(new FakeRepository(), local(DAY, 9, 0)).forecast(DAY.minusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Statistiken");
    }

    @Test
    void daylightSavingEndUsesTheWinterOffset() {
        LocalDate dstEnd = LocalDate.of(2026, 10, 25);

        AdminOccupancyResponse.Forecast forecast = service(new FakeRepository(), NOW).forecast(dstEnd, null);

        assertThat(forecast.hours()).hasSize(15);
        assertThat(forecast.hours().get(0).start()).isEqualTo(Instant.parse("2026-10-25T06:00:00Z"));
        assertThat(forecast.hours().get(14).end()).isEqualTo(Instant.parse("2026-10-25T21:00:00Z"));
    }

    @Test
    void inactiveBuildingsAppearOnlyWithBookings() {
        FakeRepository repository = scenario();
        repository.buildings.add(new BuildingRow(BUILDING_C, "C", EntityStatus.DEACTIVATED));
        repository.buildings.add(new BuildingRow(UUID.randomUUID(), "D", EntityStatus.DEACTIVATED));
        repository.overlapping.add(new BookingRow(UUID.randomUUID(), UUID.randomUUID(), "C1", BUILDING_C, "C",
                EntityStatus.DEACTIVATED, UUID.randomUUID(), "EG", local(DAY, 8, 0), local(DAY, 9, 0),
                local(DAY, 8, 0), ReservationStatus.RESERVED, 2, null));

        AdminOccupancyResponse.Forecast forecast = service(repository, NOW).forecast(DAY, null);

        assertThat(forecast.buildings()).extracting(AdminOccupancyResponse.ForecastBuilding::buildingName)
                .containsExactly("A", "B", "C");
        assertThat(forecast.buildings().get(2).buildingActive()).isFalse();
    }

    @Test
    void buildingFilterLimitsTheForecastToThatBuilding() {
        AdminOccupancyResponse.Forecast forecast = service(scenario(), NOW).forecast(DAY, BUILDING_A);

        assertThat(forecast.buildingId()).isEqualTo(BUILDING_A);
        assertThat(forecast.buildings()).extracting(AdminOccupancyResponse.ForecastBuilding::buildingId)
                .containsExactly(BUILDING_A);
        AdminOccupancyResponse.HourForecast ten = hour(forecast, "10:00");
        assertThat(ten.expectedPersons()).isEqualTo(17);
        assertThat(ten.bookedRooms()).isEqualTo(2);
        assertThat(ten.floors()).extracting(AdminOccupancyResponse.FloorGroup::buildingId).containsOnly(BUILDING_A);
    }

    @Test
    void forecastRejectsUnknownBuilding() {
        assertThatThrownBy(() -> service(new FakeRepository(), NOW).forecast(DAY, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- Running bookings (US3) ----

    @Test
    void runningBookingsListEveryCoveringBookingOnceWithItsCheckInStatus() {
        FakeRepository repository = new FakeRepository();
        BookingRow r3 = active(ROOM_3, 10);
        BookingRow r2 = reserved(ROOM_2, 6);
        BookingRow r1 = active(ROOM_1, 4);
        repository.covering.addAll(List.of(r3, r2, r1));

        AdminOccupancyResponse.Snapshot snapshot = service(repository, NOW).snapshot(null);

        assertThat(snapshot.runningBookings()).extracting(AdminOccupancyResponse.BookingEntry::reservationId)
                .containsExactly(r1.reservationId(), r2.reservationId(), r3.reservationId());
        AdminOccupancyResponse.BookingEntry reservedEntry = snapshot.runningBookings().get(1);
        assertThat(reservedEntry.checkInStatus()).isEqualTo(AdminOccupancyResponse.CheckInStatus.NOT_CHECKED_IN);
        assertThat(reservedEntry.checkedInAt()).isNull();
        AdminOccupancyResponse.BookingEntry activeEntry = snapshot.runningBookings().get(0);
        assertThat(activeEntry.checkInStatus()).isEqualTo(AdminOccupancyResponse.CheckInStatus.CHECKED_IN);
        assertThat(activeEntry.checkedInAt()).isEqualTo(r1.checkedInAt());
        assertThat(activeEntry.floorName()).isEqualTo("EG");
        assertThat(activeEntry.buildingName()).isEqualTo("A");
        assertThat(snapshot.totals().checkedInPersons()).isEqualTo(14);
    }

    // ---- Building breakdown (US4) ----

    @Test
    void buildingBreakdownListsActiveBuildingsAndInactiveOnesWithValues() {
        FakeRepository repository = new FakeRepository();
        repository.buildings.add(new BuildingRow(BUILDING_C, "C", EntityStatus.DEACTIVATED));
        repository.buildings.add(new BuildingRow(UUID.randomUUID(), "D", EntityStatus.DEACTIVATED));
        repository.buildings.add(new BuildingRow(UUID.randomUUID(), "E", EntityStatus.ACTIVE));
        repository.covering.addAll(List.of(active(ROOM_1, 8), active(ROOM_3, 4), active(ROOM_3, 2), reserved(ROOM_2, 9)));
        repository.covering.add(new BookingRow(UUID.randomUUID(), UUID.randomUUID(), "C1", BUILDING_C, "C",
                EntityStatus.DEACTIVATED, UUID.randomUUID(), "EG", NOW.minusSeconds(600), NOW.plusSeconds(600),
                NOW.minusSeconds(600), ReservationStatus.ACTIVE, 3, NOW.minusSeconds(600)));

        AdminOccupancyResponse.Snapshot snapshot = service(repository, NOW).snapshot(null);

        assertThat(snapshot.buildings()).extracting(AdminOccupancyResponse.BuildingOccupancy::buildingName,
                        AdminOccupancyResponse.BuildingOccupancy::buildingActive,
                        AdminOccupancyResponse.BuildingOccupancy::checkedInPersons,
                        AdminOccupancyResponse.BuildingOccupancy::occupiedRooms)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("A", true, 8, 1),
                        org.assertj.core.groups.Tuple.tuple("B", true, 6, 1),
                        org.assertj.core.groups.Tuple.tuple("C", false, 3, 1),
                        org.assertj.core.groups.Tuple.tuple("E", true, 0, 0));
        assertThat(snapshot.buildings().stream().mapToInt(AdminOccupancyResponse.BuildingOccupancy::checkedInPersons).sum())
                .isEqualTo(snapshot.totals().checkedInPersons());
        assertThat(snapshot.buildings().stream().mapToInt(AdminOccupancyResponse.BuildingOccupancy::occupiedRooms).sum())
                .isEqualTo(snapshot.totals().occupiedRooms());
    }

    @Test
    void buildingFilterLimitsTheSnapshotToThatBuilding() {
        FakeRepository repository = new FakeRepository();
        repository.covering.addAll(List.of(active(ROOM_1, 8), active(ROOM_3, 6), reserved(ROOM_2, 9)));

        AdminOccupancyResponse.Snapshot snapshot = service(repository, NOW).snapshot(BUILDING_A);

        assertThat(snapshot.buildingId()).isEqualTo(BUILDING_A);
        assertThat(snapshot.buildings()).singleElement().satisfies(b -> {
            assertThat(b.buildingId()).isEqualTo(BUILDING_A);
            assertThat(b.checkedInPersons()).isEqualTo(8);
        });
        assertThat(snapshot.runningBookings()).extracting(AdminOccupancyResponse.BookingEntry::buildingId)
                .containsOnly(BUILDING_A);
        assertThat(snapshot.totals().checkedInPersons()).isEqualTo(8);
    }
}
