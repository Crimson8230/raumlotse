package at.mci.igp.raumlotse.service;

import static org.assertj.core.api.Assertions.assertThat;

import at.mci.igp.raumlotse.domain.PathSegment;
import at.mci.igp.raumlotse.domain.PathWaypoint;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PathNetworkIssueCheckerTest {

    final UUID map = UUID.randomUUID();
    final PathNetworkIssueChecker checker = new PathNetworkIssueChecker();

    PathWaypoint wp() {
        var w = new PathWaypoint(map, 0.5, 0.5);
        ReflectionTestUtils.setField(w, "id", UUID.randomUUID());
        return w;
    }

    PathSegment seg(PathWaypoint a, PathWaypoint b) {
        UUID[] pair = PathGeometry.orderedPair(a.getId(), b.getId());
        return new PathSegment(map, pair[0], pair[1]);
    }

    PathNetworkIssueChecker.Input input(boolean scale, List<PathWaypoint> w, List<PathSegment> s, Set<UUID> rooms,
            Map<UUID, UUID> roomAtt, Set<UUID> points, Map<UUID, UUID> pointAtt) {
        return new PathNetworkIssueChecker.Input(scale, w, s, rooms, roomAtt, points, pointAtt);
    }

    @Test
    void emptyMapHasNoComponentsAndReportsOnlyMissingScale() {
        var issues = checker.check(input(false, List.of(), List.of(), Set.of(), Map.of(), Set.of(), Map.of()));
        assertThat(issues.componentCount()).isZero();
        assertThat(issues.components()).isEmpty();
        assertThat(issues.missingScale()).isTrue();
        assertThat(issues.attachmentsOutsideMainComponent()).isEmpty();
    }

    @Test
    void componentsAreListedLargestFirst() {
        var a = wp(); var b = wp(); var c = wp(); var d = wp(); var e = wp();
        var issues = checker.check(input(true, List.of(a, b, c, d, e), List.of(seg(a, b), seg(c, d), seg(d, e)),
                Set.of(), Map.of(), Set.of(), Map.of()));
        assertThat(issues.componentCount()).isEqualTo(2);
        assertThat(issues.components().get(0)).containsExactlyInAnyOrder(c.getId(), d.getId(), e.getId());
        assertThat(issues.components().get(1)).containsExactlyInAnyOrder(a.getId(), b.getId());
        assertThat(issues.missingScale()).isFalse();
    }

    @Test
    void crossingSegmentsWithoutSharedWaypointStayUnconnected() {
        var a = wp(); var b = wp(); var c = wp(); var d = wp();
        var issues = checker.check(input(true, List.of(a, b, c, d), List.of(seg(a, b), seg(c, d)),
                Set.of(), Map.of(), Set.of(), Map.of()));
        assertThat(issues.componentCount()).isEqualTo(2);
    }

    @Test
    void listsUnattachedRoomsAndConnectionPoints() {
        var a = wp();
        UUID attachedRoom = UUID.randomUUID();
        UUID looseRoom = UUID.randomUUID();
        UUID attachedPoint = UUID.randomUUID();
        UUID loosePoint = UUID.randomUUID();
        var issues = checker.check(input(true, List.of(a), List.of(), Set.of(attachedRoom, looseRoom),
                Map.of(attachedRoom, a.getId()), Set.of(attachedPoint, loosePoint), Map.of(attachedPoint, a.getId())));
        assertThat(issues.unattachedRoomIds()).containsExactly(looseRoom);
        assertThat(issues.unattachedConnectionPointIds()).containsExactly(loosePoint);
    }

    @Test
    void flagsAttachmentsOutsideTheMainComponent() {
        var a = wp(); var b = wp(); var c = wp();
        UUID mainRoom = UUID.randomUUID();
        UUID islandRoom = UUID.randomUUID();
        UUID islandPoint = UUID.randomUUID();
        var issues = checker.check(input(true, List.of(a, b, c), List.of(seg(a, b)),
                Set.of(mainRoom, islandRoom), Map.of(mainRoom, a.getId(), islandRoom, c.getId()),
                Set.of(islandPoint), Map.of(islandPoint, c.getId())));
        assertThat(issues.attachmentsOutsideMainComponent())
                .extracting("kind", "id")
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("ROOM", islandRoom),
                        org.assertj.core.groups.Tuple.tuple("CONNECTION_POINT", islandPoint));
    }

    @Test
    void completeMapHasNoIssues() {
        var a = wp(); var b = wp();
        UUID room = UUID.randomUUID();
        UUID point = UUID.randomUUID();
        var issues = checker.check(input(true, List.of(a, b), List.of(seg(a, b)), Set.of(room),
                Map.of(room, a.getId()), Set.of(point), Map.of(point, b.getId())));
        assertThat(issues.missingScale()).isFalse();
        assertThat(issues.componentCount()).isEqualTo(1);
        assertThat(issues.unattachedRoomIds()).isEmpty();
        assertThat(issues.unattachedConnectionPointIds()).isEmpty();
        assertThat(issues.attachmentsOutsideMainComponent()).isEmpty();
    }
}
