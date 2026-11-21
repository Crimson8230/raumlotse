package at.mci.igp.raumlotse.service;

/** Fixed routing constants (feature 024). Not configurable. */
public final class RoutingDefaults {

    /** Walking speed in meters per second. */
    public static final double WALKING_SPEED_MPS = 1.2;

    /** Cost of taking stairs, per floor step, in seconds. */
    public static final double STAIRS_SECONDS_PER_FLOOR = 15.0;

    /** Cost of an elevator change (waiting plus ride, any number of floors), in seconds. */
    public static final double ELEVATOR_SECONDS_PER_CHANGE = 30.0;

    /** A neighbor whose first step deviates at most this many degrees from the facing direction is "straight". */
    public static final double STRAIGHT_TOLERANCE_DEGREES = 20.0;

    /** Maximum number of room neighbors per direction. */
    public static final int MAX_NEIGHBORS_PER_SIDE = 3;

    private RoutingDefaults() {
    }
}
