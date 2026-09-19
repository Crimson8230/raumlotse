package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.EntityStatus;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.Room;
import at.mci.igp.raumlotse.domain.SeatingArrangement;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.RoomService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomController.class)
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomService roomService;

    private Room roomWithOneSeatingArrangement() {
        Building building = new Building("Main");
        Floor floor = new Floor(building, "1");
        Room room = new Room("Room 101", floor);
        room.replaceSeatingArrangements(List.of(new SeatingArrangement("Theater", 40)));
        return room;
    }

    @Test
    void createReturns201() throws Exception {
        when(roomService.create(any())).thenReturn(roomWithOneSeatingArrangement());

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Room 101",
                                  "floorId": "%s",
                                  "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Room 101"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.seatingArrangements", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void createWithZeroSeatingArrangementsReturns400() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "seatingArrangements": []}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithMissingNameReturns400() throws Exception {
        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"floorId": "%s", "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithDuplicateNameInBuildingReturns409() throws Exception {
        when(roomService.create(any()))
                .thenThrow(new ConflictException("A room named 'Room 101' already exists in this building."));

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void createWithDuplicateSeatingArrangementNameReturns409() throws Exception {
        when(roomService.create(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate seating arrangement name"));

        mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "seatingArrangements": [
                                  {"name": "Theater", "maxCapacity": 40},
                                  {"name": "Theater", "maxCapacity": 20}
                                ]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturns200WithStatusFilter() throws Exception {
        when(roomService.list(any())).thenReturn(List.of(roomWithOneSeatingArrangement()));

        mockMvc.perform(get("/api/rooms?status=active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void getReturns200WithFullConfiguration() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.get(id)).thenReturn(roomWithOneSeatingArrangement());

        mockMvc.perform(get("/api/rooms/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Room 101"));
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.get(id)).thenThrow(new NotFoundException("Room " + id + " not found."));

        mockMvc.perform(get("/api/rooms/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.update(eq(id), any())).thenReturn(roomWithOneSeatingArrangement());

        mockMvc.perform(put("/api/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "version": 0,
                                 "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    void updateWithEmptySeatingArrangementsReturns400() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "version": 0, "seatingArrangements": []}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateWithStaleVersionReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.update(eq(id), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/api/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "version": 0,
                                 "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void updateWithCollidingNameReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.update(eq(id), any()))
                .thenThrow(new ConflictException("A room named 'Room 101' already exists in this building."));

        mockMvc.perform(put("/api/rooms/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Room 101", "floorId": "%s", "version": 0,
                                 "seatingArrangements": [{"name": "Theater", "maxCapacity": 40}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        Room deactivated = roomWithOneSeatingArrangement();
        deactivated.setStatus(EntityStatus.DEACTIVATED);
        when(roomService.deactivate(id)).thenReturn(deactivated);

        mockMvc.perform(post("/api/rooms/" + id + "/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEACTIVATED"));
    }

    @Test
    void reactivateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(roomService.reactivate(id)).thenReturn(roomWithOneSeatingArrangement());

        mockMvc.perform(post("/api/rooms/" + id + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void deleteReturns204WhenNoDependentHistory() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/rooms/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteBlockedByDependentHistoryReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.doThrow(new ConflictException("Room has dependent history; deactivate instead."))
                .when(roomService).delete(id);

        mockMvc.perform(delete("/api/rooms/" + id))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateBlockedByActiveReservationsReturns409() throws Exception {
        UUID id = UUID.randomUUID();
        Mockito.when(roomService.deactivate(id))
                .thenThrow(new ConflictException("Room has active or upcoming reservations; cancel them first."));

        mockMvc.perform(post("/api/rooms/" + id + "/deactivate"))
                .andExpect(status().isConflict());
    }
}
