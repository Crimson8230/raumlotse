package at.mci.igp.raumlotse.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.mci.igp.raumlotse.config.SecurityConfig;
import at.mci.igp.raumlotse.dto.AuthenticatedUser;
import at.mci.igp.raumlotse.dto.MapDetailResponse;
import at.mci.igp.raumlotse.dto.MapSummaryResponse;
import at.mci.igp.raumlotse.exception.MapRequestException;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.service.FloorMapService;
import at.mci.igp.raumlotse.service.UserRoleSafety;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FloorMapController.class)
@WithMockUser
@Import(SecurityConfig.class)
class FloorMapControllerTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private at.mci.igp.raumlotse.service.EffectivePermissionService effectivePermissions;
    @Autowired MockMvc mvc;
    @MockitoBean FloorMapService service;
    // Writes pass through RoleAccessFilter; the role check itself is covered by MapAuthorizationIntegrationTest.
    @MockitoBean UserRoleSafety roleSafety;

    static RequestPostProcessor admin() {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(UUID.randomUUID(), "Admin"), null, List.of()));
    }

    final UUID mapId = UUID.randomUUID();
    final UUID floorId = UUID.randomUUID();

    MapSummaryResponse summary(Boolean aspect) {
        return new MapSummaryResponse(mapId, floorId, "Haus A – EG", 200, 100, 1, 0, aspect);
    }

    MockMultipartFile image() {
        return new MockMultipartFile("image", "plan.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    void listReturnsMaps() throws Exception {
        when(service.list()).thenReturn(List.of(summary(null)));
        mvc.perform(get("/api/maps").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Haus A – EG"))
                .andExpect(jsonPath("$[0].placedRoomCount").value(0))
                .andExpect(jsonPath("$[0].aspectRatioChanged").doesNotExist());
    }

    @Test
    void putCreatesMapWith201() throws Exception {
        when(service.saveForFloor(eq(floorId), any())).thenReturn(new FloorMapService.SaveResult(summary(null), true));
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").file(image()).with(admin()).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(mapId.toString()));
    }

    @Test
    void putReplacesImageWith200AndAspectWarning() throws Exception {
        when(service.saveForFloor(eq(floorId), any())).thenReturn(new FloorMapService.SaveResult(summary(true), false));
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").file(image()).with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aspectRatioChanged").value(true));
    }

    @Test
    void putWithUnknownFloorReturns404() throws Exception {
        when(service.saveForFloor(eq(floorId), any())).thenThrow(new NotFoundException("Floor nicht gefunden."));
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").file(image()).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void putWithUnsupportedImageReturns415() throws Exception {
        when(service.saveForFloor(eq(floorId), any())).thenThrow(new MapRequestException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MAP_IMAGE_UNSUPPORTED", "Es werden nur PNG- und JPEG-Bilder unterstützt."));
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").file(image()).with(admin()).with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MAP_IMAGE_UNSUPPORTED"));
    }

    @Test
    void putWithOversizedImageReturns413() throws Exception {
        when(service.saveForFloor(eq(floorId), any())).thenThrow(new MapRequestException(
                HttpStatus.CONTENT_TOO_LARGE, "MAP_IMAGE_TOO_LARGE", "Das Bild ist größer als 10 MB."));
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").file(image()).with(admin()).with(csrf()))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("MAP_IMAGE_TOO_LARGE"));
    }

    @Test
    void putWithoutImagePartReturns400() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/floors/" + floorId + "/map").with(admin()).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsDetail() throws Exception {
        when(service.get(mapId)).thenReturn(
                new MapDetailResponse(mapId, floorId, "Haus A – EG", 200, 100, 1, 0, List.of(), List.of()));
        mvc.perform(get("/api/maps/" + mapId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements").isArray())
                .andExpect(jsonPath("$.connections").isArray());
    }

    @Test
    void getUnknownReturns404() throws Exception {
        when(service.get(mapId)).thenThrow(new NotFoundException("Map nicht gefunden."));
        mvc.perform(get("/api/maps/" + mapId).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void imageIsServedWithEtagAndNosniff() throws Exception {
        when(service.image(mapId)).thenReturn(new FloorMapService.MapImage("image/png", 3, new byte[] {1, 2, 3}));
        mvc.perform(get("/api/maps/" + mapId + "/image").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {1, 2, 3}));
    }

    @Test
    void imageReturns304WhenEtagMatches() throws Exception {
        when(service.image(mapId)).thenReturn(new FloorMapService.MapImage("image/png", 3, new byte[] {1, 2, 3}));
        mvc.perform(get("/api/maps/" + mapId + "/image").header("If-None-Match", "\"3\"").with(admin()))
                .andExpect(status().isNotModified());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/maps/" + mapId).with(admin()).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        doThrow(new NotFoundException("Map nicht gefunden.")).when(service).delete(mapId);
        mvc.perform(delete("/api/maps/" + mapId).with(admin()).with(csrf())).andExpect(status().isNotFound());
    }
}
