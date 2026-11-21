package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PathGeometryTest {

    @Test
    void pixelDistanceUsesImageDimensions() {
        assertThat(PathGeometry.pixelDistance(0, 0, 0.5, 0, 200, 100)).isCloseTo(100.0, within(1e-9));
        assertThat(PathGeometry.pixelDistance(0, 0, 0, 0.5, 200, 100)).isCloseTo(50.0, within(1e-9));
        assertThat(PathGeometry.pixelDistance(0, 0, 0.3, 0.4, 100, 100)).isCloseTo(50.0, within(1e-9));
    }

    @Test
    void projectsPointOntoSegment() {
        var p = PathGeometry.project(0.5, 0.3, 0.0, 0.0, 1.0, 0.0);
        assertThat(p.x()).isCloseTo(0.5, within(1e-9));
        assertThat(p.y()).isCloseTo(0.0, within(1e-9));
        assertThat(p.t()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void projectionIsClampedToTheSegmentEnds() {
        var before = PathGeometry.project(-0.5, 0.1, 0.0, 0.0, 1.0, 0.0);
        assertThat(before.t()).isEqualTo(0.0);
        assertThat(before.x()).isEqualTo(0.0);
        var after = PathGeometry.project(2.0, 0.1, 0.0, 0.0, 1.0, 0.0);
        assertThat(after.t()).isEqualTo(1.0);
        assertThat(after.x()).isEqualTo(1.0);
    }

    @Test
    void projectionOnDegenerateSegmentReturnsItsStart() {
        var p = PathGeometry.project(0.9, 0.9, 0.2, 0.2, 0.2, 0.2);
        assertThat(p.x()).isEqualTo(0.2);
        assertThat(p.t()).isEqualTo(0.0);
    }

    @Test
    void coincidenceThresholdIsTwoThousandthsOfTheRelativeDiagonal() {
        assertThat(PathGeometry.coincide(0.5, 0.5, 0.5, 0.5)).isTrue();
        assertThat(PathGeometry.coincide(0.5, 0.5, 0.5015, 0.5)).isTrue();
        assertThat(PathGeometry.coincide(0.5, 0.5, 0.504, 0.5)).isFalse();
    }

    @Test
    void pairIsNormalizedLikeTheDatabaseOrdersUuids() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffffe");
        assertThat(PathGeometry.orderedPair(high, low)).containsExactly(low, high);
        assertThat(PathGeometry.orderedPair(low, high)).containsExactly(low, high);
    }
}
