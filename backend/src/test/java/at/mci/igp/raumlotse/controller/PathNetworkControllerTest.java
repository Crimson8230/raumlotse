package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.ConnectionPointAttachmentResponse;
import at.mci.igp.raumlotse.dto.MapNetworkResponse;
import at.mci.igp.raumlotse.dto.NetworkWideResponse;
import at.mci.igp.raumlotse.dto.PathIssuesResponse;
import at.mci.igp.raumlotse.dto.ReferenceSegmentResponse;
import at.mci.igp.raumlotse.dto.RoomAttachmentResponse;
import at.mci.igp.raumlotse.dto.ScaleResponse;
import at.mci.igp.raumlotse.dto.SegmentResponse;
import at.mci.igp.raumlotse.dto.SplitResultResponse;
import at.mci.igp.raumlotse.dto.WaypointResponse;
import at.mci.igp.raumlotse.exception.ConflictException;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.MapScaleService;
import at.mci.igp.raumlotse.service.PathNetworkService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(PathNetworkController.class)
@WithMockUser
@Import(SecurityConfig.class)
class PathNetworkControllerTest {

    @MockitoBean at.mci.igp.raumlotse.service.EffectivePermissionService effectivePermissions;
    @Autowired MockMvc mvc;
    @MockitoBean PathNetworkService service;
    @MockitoBean MapScaleService scale;
    @MockitoBean UserRoleSafety roleSafety;

    final UUID mapId = UUID.randomUUID();
    final UUID wp = UUID.randomUUID();
    final UUID wp2 = UUID.randomUUID();
    final UUID segId = UUID.randomUUID();
    final UUID roomId = UUID.randomUUID();
    static final String POS = "{\"x\":0.25,\"y\":0.5}";

    static RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), "Admin"), null, List.of()));
    }

    String base() {
        return "/api/maps/" + mapId;
    }

    MapNetworkResponse emptyNetwork() {
        return new MapNetworkResponse(mapId, new ScaleResponse(false, null, 200, 100), null, List.of(), List.of(),
                List.of(), List.of(), new PathIssuesResponse(true, 0, List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    void getMapNetworkReturnsContractShape() throws Exception {
        when(service.mapNetwork(mapId)).thenReturn(emptyNetwork());
        mvc.perform(get(base() + "/path-network").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mapId").value(mapId.toString()))
                .andExpect(jsonPath("$.scale.available").value(false))
                .andExpect(jsonPath("$.issues.missingScale").value(true))
                .andExpect(jsonPath("$.waypoints").isArray());
    }

    @Test
    void getMapNetworkOfUnknownMapReturns404() throws Exception {
        when(service.mapNetwork(mapId)).thenThrow(new NotFoundException("Karte nicht gefunden."));
        mvc.perform(get(base() + "/path-network").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void getNetworkWideReturnsMapsAndGroups() throws Exception {
        when(service.network()).thenReturn(new NetworkWideResponse(List.of(emptyNetwork()), List.of()));
        mvc.perform(get("/api/path-network").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maps.length()").value(1))
                .andExpect(jsonPath("$.connectionGroups").isArray());
    }

    @Test
    void addWaypointReturns201() throws Exception {
        when(service.addWaypoint(mapId, 0.25, 0.5)).thenReturn(new WaypointResponse(wp, mapId, 0.25, 0.5));
        mvc.perform(post(base() + "/waypoints").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(wp.toString()));
    }

    @Test
    void coordinatesOutsideRangeOrMissingReturn400() throws Exception {
        for (String body : List.of("{\"x\":1.2,\"y\":0.5}", "{\"x\":0.5,\"y\":-0.1}", "{\"x\":0.5}", "{}")) {
            mvc.perform(post(base() + "/waypoints").with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).addWaypoint(eq(mapId), anyDouble(), anyDouble());
    }

    @Test
    void coincidingWaypointReturns409() throws Exception {
        when(service.addWaypoint(mapId, 0.25, 0.5)).thenThrow(new ConflictException("Schon vorhanden.", "WAYPOINT_COINCIDES"));
        mvc.perform(post(base() + "/waypoints").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POS))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAYPOINT_COINCIDES"));
    }

    @Test
    void moveAndDeleteWaypoint() throws Exception {
        when(service.moveWaypoint(mapId, wp, 0.25, 0.5)).thenReturn(new WaypointResponse(wp, mapId, 0.25, 0.5));
        mvc.perform(put(base() + "/waypoints/" + wp).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POS))
                .andExpect(status().isOk());
        mvc.perform(delete(base() + "/waypoints/" + wp).with(admin()).with(csrf())).andExpect(status().isNoContent());
        verify(service).deleteWaypoint(mapId, wp);
    }

    @Test
    void segmentCreationStatusCodes() throws Exception {
        String body = "{\"fromWaypointId\":\"" + wp + "\",\"toWaypointId\":\"" + wp2 + "\"}";
        when(service.addSegment(mapId, wp, wp2)).thenReturn(new SegmentResponse(segId, mapId, wp, wp2, null));
        mvc.perform(post(base() + "/segments").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        org.mockito.Mockito.doThrow(new ConflictException("Schon da.", "SEGMENT_EXISTS")).when(service).addSegment(mapId, wp, wp2);
        mvc.perform(post(base() + "/segments").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        org.mockito.Mockito.doThrow(new MapRequestException(
                HttpStatus.UNPROCESSABLE_CONTENT, "WAYPOINT_MAP_MISMATCH", "Andere Karte.")).when(service).addSegment(mapId, wp, wp2);
        mvc.perform(post(base() + "/segments").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("WAYPOINT_MAP_MISMATCH"));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Zwei Wegpunkte nötig.")).when(service).addSegment(mapId, wp, wp2);
        mvc.perform(post(base() + "/segments").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base() + "/segments").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSegmentAndSplit() throws Exception {
        mvc.perform(delete(base() + "/segments/" + segId).with(admin()).with(csrf())).andExpect(status().isNoContent());
        when(service.splitSegment(mapId, segId, 0.25, 0.5)).thenReturn(new SplitResultResponse(
                new WaypointResponse(wp, mapId, 0.25, 0.5),
                List.of(new SegmentResponse(UUID.randomUUID(), mapId, wp, wp2, null),
                        new SegmentResponse(UUID.randomUUID(), mapId, wp, wp2, null))));
        mvc.perform(post(base() + "/segments/" + segId + "/split").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(POS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.segments.length()").value(2));
    }

    @Test
    void referenceSegmentStatusCodes() throws Exception {
        String body = "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":10}";
        var response = new ReferenceSegmentResponse(mapId, 0.1, 0.5, 0.6, 0.5, 10, 10);
        when(scale.set(eq(mapId), any())).thenReturn(new MapScaleService.ReferenceResult(response, true));
        mvc.perform(put(base() + "/reference-segment").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pixelsPerMeter").value(10.0));
        when(scale.set(eq(mapId), any())).thenReturn(new MapScaleService.ReferenceResult(response, false));
        mvc.perform(put(base() + "/reference-segment").with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        for (String bad : List.of("{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":0}",
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":-1}",
                "{\"x1\":0.1,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5}",
                "{\"x1\":2,\"y1\":0.5,\"x2\":0.6,\"y2\":0.5,\"lengthMeters\":4}")) {
            mvc.perform(put(base() + "/reference-segment").with(admin()).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(delete(base() + "/reference-segment").with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(scale).delete(mapId);
    }

    @Test
    void roomAttachmentStatusCodes() throws Exception {
        String body = "{\"waypointId\":\"" + wp + "\"}";
        var created = new PathNetworkService.AttachResult<>(new RoomAttachmentResponse(roomId, mapId, wp), true);
        when(service.attachRoom(mapId, roomId, wp)).thenReturn(created);
        mvc.perform(put(base() + "/room-attachments/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        when(service.attachRoom(mapId, roomId, wp)).thenReturn(
                new PathNetworkService.AttachResult<>(new RoomAttachmentResponse(roomId, mapId, wp), false));
        mvc.perform(put(base() + "/room-attachments/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        org.mockito.Mockito.doThrow(new MapRequestException(
                HttpStatus.UNPROCESSABLE_CONTENT, "ROOM_NOT_PLACED_ON_MAP", "Nicht platziert.")).when(service).attachRoom(mapId, roomId, wp);
        mvc.perform(put(base() + "/room-attachments/" + roomId).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(delete(base() + "/room-attachments/" + roomId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void connectionPointAttachmentStatusCodes() throws Exception {
        UUID point = UUID.randomUUID();
        String body = "{\"waypointId\":\"" + wp + "\"}";
        when(service.attachConnectionPoint(mapId, point, wp)).thenReturn(new PathNetworkService.AttachResult<>(
                new ConnectionPointAttachmentResponse(point, mapId, wp), true));
        mvc.perform(put(base() + "/connection-point-attachments/" + point).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(put(base() + "/connection-point-attachments/" + point).with(admin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(base() + "/connection-point-attachments/" + point).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());
        verify(service).removeConnectionPointAttachment(mapId, point);
    }
}
