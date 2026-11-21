package at.mci.igp.raumlotse.service;

import java.util.UUID;

/** Pure geometry helpers for the path network. Positions are fractions (0..1) of the map image. */
public final class PathGeometry {

    /** Two points closer than this (2/1000 of the relative diagonal) count as the same point. */
    public static final double COINCIDENCE_EPSILON = 0.002 * Math.sqrt(2.0);

    public record Projection(double x, double y, double t) {
    }

    private PathGeometry() {
    }

    public static double pixelDistance(double x1, double y1, double x2, double y2, int widthPx, int heightPx) {
        return Math.hypot((x2 - x1) * widthPx, (y2 - y1) * heightPx);
    }

    public static boolean coincide(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1) < COINCIDENCE_EPSILON;
    }

    /** Projects a point onto the segment a-b; the parameter t is clamped to [0, 1]. */
    public static Projection project(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared == 0.0) {
            return new Projection(ax, ay, 0.0);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Projection(ax + t * dx, ay + t * dy, t);
    }

    /**
     * Orders two ids the way PostgreSQL orders uuids (unsigned byte-wise), which equals the order of their
     * canonical lowercase string form. {@link UUID#compareTo} is signed and must not be used here.
     */
    public static UUID[] orderedPair(UUID a, UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? new UUID[] {a, b} : new UUID[] {b, a};
    }
}
