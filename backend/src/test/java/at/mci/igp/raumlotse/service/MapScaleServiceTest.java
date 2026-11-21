package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.mci.igp.raumlotse.domain.Building;
import at.mci.igp.raumlotse.domain.Floor;
import at.mci.igp.raumlotse.domain.FloorMap;
import at.mci.igp.raumlotse.domain.MapReferenceSegment;
import at.mci.igp.raumlotse.dto.ReferenceSegmentRequest;
import at.mci.igp.raumlotse.dto.ScaleResponse;
import at.mci.igp.raumlotse.exception.NotFoundException;
import at.mci.igp.raumlotse.repository.FloorMapRepository;
import at.mci.igp.raumlotse.repository.MapReferenceSegmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapScaleServiceTest {

    @Mock FloorMapRepository maps;
    @Mock MapReferenceSegmentRepository references;

    MapScaleService service;
    FloorMap map; // 200 x 100 px

    @BeforeEach
    void setUp() {
        service = new MapScaleService(maps, references);
        Floor floor = new Floor(new Building("Haus A"), "EG");
        map = new FloorMap(floor, "image/png", 200, 100);
        ReflectionTestUtils.setField(map, "id", UUID.randomUUID());
        when(maps.findById(map.getId())).thenReturn(Optional.of(map));
    }

    static ReferenceSegmentRequest ref(double x1, double y1, double x2, double y2, double length) {
        return new ReferenceSegmentRequest(x1, y1, x2, y2, length);
    }

    @Test
    void scaleIsUnavailableWithoutReferenceSegment() {
        ScaleResponse scale = MapScaleService.scaleOf(map, null);
        assertThat(scale.available()).isFalse();
        assertThat(scale.pixelsPerMeter()).isNull();
        assertThat(scale.widthPx()).isEqualTo(200);
        assertThat(MapScaleService.lengthMeters(scale, 0, 0, 0.5, 0)).isNull();
    }

    @Test
    void derivesPixelsPerMeterFromReferenceAndImageSize() {
        // 0.5 of the width = 100 px for 10 m => 10 px/m
        var reference = new MapReferenceSegment(map.getId(), 0.1, 0.5, 0.6, 0.5, 10);
        ScaleResponse scale = MapScaleService.scaleOf(map, reference);
        assertThat(scale.available()).isTrue();
        assertThat(scale.pixelsPerMeter()).isCloseTo(10.0, within(1e-9));
        // 0.25 of the width = 50 px => 5 m
        assertThat(MapScaleService.lengthMeters(scale, 0.0, 0.2, 0.25, 0.2)).isCloseTo(5.0, within(1e-9));
        // 0.5 of the height = 50 px => 5 m
        assertThat(MapScaleService.lengthMeters(scale, 0.3, 0.0, 0.3, 0.5)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void scaleStaysConsistentWhenImageIsReplacedWithSameAspectRatio() {
        var reference = new MapReferenceSegment(map.getId(), 0.1, 0.5, 0.6, 0.5, 10);
        map.replaceImage("image/png", 400, 200);
        ScaleResponse scale = MapScaleService.scaleOf(map, reference);
        assertThat(scale.pixelsPerMeter()).isCloseTo(20.0, within(1e-9));
        assertThat(MapScaleService.lengthMeters(scale, 0.0, 0.2, 0.25, 0.2)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void setCreatesThenReplacesTheSingleReference() {
        when(references.findById(map.getId())).thenReturn(Optional.empty());
        var created = service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, 10));
        assertThat(created.created()).isTrue();
        assertThat(created.reference().pixelsPerMeter()).isCloseTo(10.0, within(1e-9));

        var existing = new MapReferenceSegment(map.getId(), 0.1, 0.5, 0.6, 0.5, 10);
        when(references.findById(map.getId())).thenReturn(Optional.of(existing));
        var replaced = service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, 20));
        assertThat(replaced.created()).isFalse();
        assertThat(existing.getLengthM()).isEqualTo(20.0);
        assertThat(replaced.reference().pixelsPerMeter()).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void rejectsLengthThatIsNotPositiveOrTooLarge() {
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, -3)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, 100001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.1, 0.5, 0.6, 0.5, Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdenticalOrOutOfBoundsEndpoints() {
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.4, 0.4, 0.4, 0.4, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.4, 0.4, 0.4005, 0.4, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(-0.1, 0.4, 0.5, 0.4, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.set(map.getId(), ref(0.1, 0.4, 1.5, 0.4, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownMapIsNotFound() {
        assertThatThrownBy(() -> service.set(UUID.randomUUID(), ref(0.1, 0.5, 0.6, 0.5, 10)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesTheReference() {
        var existing = new MapReferenceSegment(map.getId(), 0.1, 0.5, 0.6, 0.5, 10);
        when(references.findById(map.getId())).thenReturn(Optional.of(existing));
        service.delete(map.getId());
        verify(references).delete(existing);
    }

    @Test
    void deleteWithoutReferenceIsNotFound() {
        when(references.findById(map.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(map.getId())).isInstanceOf(NotFoundException.class);
        verify(references, org.mockito.Mockito.never()).delete(any());
    }
}
