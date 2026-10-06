package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.RoomSearchCriteria;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.dto.RoomResponse;
import at.mci.igp.raumlotse.repository.RoomRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomSearchServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private at.mci.igp.raumlotse.repository.ReservationRepository reservationRepository;

    private RoomSearchService service;

    @BeforeEach
    void setUp() {
        service = new RoomSearchService(roomRepository, reservationRepository);
    }

    private static Room room(String buildingName, String roomName, int capacity) {
        Room room = new Room(roomName, new Floor(new Building(buildingName), "EG"));
        room.replaceSeatingArrangements(List.of(new SeatingArrangement("Theater", capacity)));
        return room;
    }

    @Test
    void rejectsMinPersonsGreaterThanMaxPersons() {
        assertThatThrownBy(() -> service.search(new RoomSearchCriteria(30, 10, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Min. Personen darf nicht größer als Max. Personen sein.");
        verify(roomRepository, never()).findSearchCandidates();
    }

    @Test
    void withoutBuildingLoadsAllCandidates() {
        when(roomRepository.findSearchCandidates()).thenReturn(List.of(room("Haus 1", "A", 40)));

        assertThat(service.search(new RoomSearchCriteria(null, null, null))).hasSize(1);
        verify(roomRepository, never()).findSearchCandidatesInBuilding(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void withBuildingLoadsOnlyThatBuildingsCandidates() {
        UUID buildingId = UUID.randomUUID();
        when(roomRepository.findSearchCandidatesInBuilding(buildingId)).thenReturn(List.of(room("Haus 1", "A", 40)));

        assertThat(service.search(new RoomSearchCriteria(null, null, buildingId))).hasSize(1);
        verify(roomRepository, never()).findSearchCandidates();
    }

    @Test
    void appliesCriteriaToCandidates() {
        when(roomRepository.findSearchCandidates()).thenReturn(List.of(
                room("Haus 1", "A", 40), room("Haus 1", "B", 15)));

        List<RoomResponse> result = service.search(new RoomSearchCriteria(20, null, null));

        assertThat(result).extracting(RoomResponse::name).containsExactly("A");
    }

    @Test
    void sortsByBuildingThenRoomNameCaseInsensitively() {
        when(roomRepository.findSearchCandidates()).thenReturn(List.of(
                room("haus 2", "a", 10),
                room("Haus 1", "c", 10),
                room("HAUS 1", "B", 10),
                room("Haus 1", "a", 10)));

        List<RoomResponse> result = service.search(new RoomSearchCriteria(null, null, null));

        assertThat(result).extracting(r -> r.building().name() + "/" + r.name())
                .containsExactly("Haus 1/a", "HAUS 1/B", "Haus 1/c", "haus 2/a");
    }

    private static Room roomWithLayouts(String... names) {
        Room room = new Room("R", new Floor(new Building("Haus 1"), "EG"));
        room.replaceSeatingArrangements(java.util.Arrays.stream(names)
                .map(name -> new SeatingArrangement(name, 10)).toList());
        return room;
    }

    @Test
    void listsDistinctSeatingArrangementNamesCaseInsensitivelySorted() {
        when(roomRepository.findSearchCandidates()).thenReturn(List.of(
                roomWithLayouts("U-Shape", "Theater"),
                roomWithLayouts("u-shape", "classroom"),
                roomWithLayouts("Boardroom")));

        assertThat(service.listSeatingArrangementNames())
                .containsExactly("Boardroom", "classroom", "Theater", "U-Shape");
    }

    private static final java.time.Instant NINE = java.time.Instant.parse("2020-01-01T09:00:00Z");

    private static RoomSearchCriteria window(java.time.Instant from, java.time.Instant to) {
        return new RoomSearchCriteria(null, null, null, null, java.util.Set.of(), false, from, to);
    }

    @Test
    void rejectsHalfAWindow() {
        assertThatThrownBy(() -> service.search(window(NINE, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("„from“ und „to“ müssen gemeinsam angegeben werden.");
        assertThatThrownBy(() -> service.search(window(null, NINE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("„from“ und „to“ müssen gemeinsam angegeben werden.");
    }

    @Test
    void rejectsAWindowWhoseEndIsNotAfterItsStart() {
        assertThatThrownBy(() -> service.search(window(NINE, NINE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("„to“ muss nach „from“ liegen.");
    }

    @Test
    void acceptsAPastWindowAndExcludesOccupiedRooms() {
        Room free = room("Haus 1", "Frei", 10);
        Room booked = room("Haus 1", "Belegt", 10);
        UUID bookedId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(booked, "id", bookedId);
        when(roomRepository.findSearchCandidates()).thenReturn(List.of(free, booked));
        when(reservationRepository.findOccupiedRoomIds(NINE, NINE.plusSeconds(3600)))
                .thenReturn(java.util.Set.of(bookedId));

        List<RoomResponse> result = service.search(window(NINE, NINE.plusSeconds(3600)));

        assertThat(result).extracting(RoomResponse::name).containsExactly("Frei");
    }

    @Test
    void doesNotQueryReservationsWithoutAWindow() {
        when(roomRepository.findSearchCandidates()).thenReturn(List.of());

        service.search(window(null, null));

        verify(reservationRepository, never()).findOccupiedRoomIds(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
