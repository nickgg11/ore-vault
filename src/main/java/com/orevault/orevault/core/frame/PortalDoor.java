package com.orevault.orevault.core.frame;

import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import java.util.ArrayList;
import java.util.List;

/**
 * The door drawn over a lit portal, and its swing on approach (§3.2, E3-S13, #401; Nick, 2026-11-08, on the rendered
 * preview in {@code docs/previews/e3-s13}). Each client works this out for its own player only: nothing here is sent or
 * stored, and the server's portal cells never change. This class is the geometry; the client draws from it.
 *
 * <p>Positions in an opening's plane are two numbers: {@code along} the opening's axis, as {@link VaultPortalShape}
 * measures it, and {@code across} it. The plane is the middle of the opening's cells. A leaf is a box: {@code s} runs
 * from its hinge to its free end, {@code t} through its thickness from the face that is toward the player when it is
 * shut, and y up the opening.
 *
 * <p>The door is laid out on a "closed" coordinate {@code x}, 0 to the width, read from the front, which is the face the
 * sealed door's model shows its part on. From behind, a viewer reads the same door, so a back face at {@code x} shows the
 * door at {@code width - x}: the back is the front mirrored, as {@link DoorPart#back()} has it.
 */
public final class PortalDoor {
    /** As a vanilla door. Change this one number for a thicker leaf. */
    public static final int LEAF_THICKNESS_PX = 3;
    /** Over a full 90 degrees; a shorter arc after turning back takes its share of it. */
    public static final int SWING_TICKS = 10;
    public static final double OPEN_DEGREES = 90;

    private static final double PIXEL = 1 / 16.0;
    private static final double MIN_OPEN_DISTANCE = 6;
    private static final double CLOSE_MARGIN = 2;
    private static final double EXTRA_PAST_HALF_WIDTH = 2;
    private static final int GEM_PX = 4;
    /** The gem's top row, counted down from the top of the seat's tile (the preview's {@code cy}). */
    private static final int GEM_TOP_ROW_PX = 4;

    private PortalDoor() {
    }

    /** Hinged on the low jamb along the axis, which a viewer at the front has on their left, or on the high one. */
    public enum Leaf {
        LEFT(1),
        RIGHT(-1);

        private final int direction;

        Leaf(int direction) {
            this.direction = direction;
        }
    }

    /** A piece of a leaf in one column of the door, as closed coordinates. */
    public record Segment(double from, double to, int column) {
    }

    /** What a segment shows on one face of one row: the part, and its texture's u at {@code from} and at {@code to}. */
    public record Tile(DoorPart part, double uFrom, double uTo) {
    }

    /**
     * A leaf placed in the plane: its hinge, the unit direction from the hinge to its free end, and the unit direction
     * through its thickness.
     */
    public record LeafFrame(double hingeAlong, double hingeAcross, double lengthAlong, double lengthAcross,
            double depthAlong, double depthAcross) {
        /** {@code {along, across}} at {@code s} from the hinge and {@code t} into the leaf. */
        public double[] point(double s, double t) {
            return new double[] {
                    hingeAlong + s * lengthAlong + t * depthAlong,
                    hingeAcross + s * lengthAcross + t * depthAcross};
        }
    }

    /**
     * A leaf's angle over time, in degrees from shut: from {@code from} at {@code start} to {@code to}, eased out. Time is
     * in ticks and may be fractional, so a frame between ticks draws between them.
     */
    public record Swing(double from, double to, double start) {
        public static Swing closed() {
            return new Swing(0, 0, 0);
        }

        /** Swings to {@code target} from wherever it is at {@code now}, so turning back mid-swing does not jump. */
        public Swing toward(double target, double now) {
            if (target == to) {
                return this;
            }
            return new Swing(angleAt(now), target, now);
        }

        public double angleAt(double now) {
            double ticks = SWING_TICKS * Math.abs(to - from) / OPEN_DEGREES;
            if (ticks <= 0) {
                return to;
            }
            return from + (to - from) * easeOut((now - start) / ticks);
        }

        /** Shut and staying shut, which is when the portal behind it is hidden. */
        public boolean isClosedAt(double now) {
            return to == 0 && angleAt(now) <= 0;
        }
    }

    public static double leafThickness() {
        return LEAF_THICKNESS_PX * PIXEL;
    }

    /** Each leaf is half the opening, so an odd width splits its centre column. */
    public static double leafLength(int width) {
        return width / 2.0;
    }

    /** Six blocks, or half the width and two more if that is further, so a wide leaf never sweeps the player. */
    public static double openDistance(int width) {
        return Math.max(MIN_OPEN_DISTANCE, leafLength(width) + EXTRA_PAST_HALF_WIDTH);
    }

    public static double closeDistance(int width) {
        return openDistance(width) + CLOSE_MARGIN;
    }

    /** Whether a door that is {@code open} now should be, with the player {@code distance} from it. */
    public static boolean wantsOpen(boolean open, double distance, int width) {
        return distance <= (open ? closeDistance(width) : openDistance(width));
    }

    /** From a point to the nearest point of the opening's rectangle, in the middle of its cells. */
    public static double distance(VaultPortalShape shape, double x, double y, double z) {
        double along = shape.axis() == Axis.X ? x : z;
        double across = shape.axis() == Axis.X ? z : x;
        double firstAlong = firstAlong(shape);
        double da = outside(along, firstAlong, firstAlong + shape.width());
        double dy = outside(y, shape.minY(), shape.minY() + shape.height());
        double dc = across - plane(shape);
        return Math.sqrt(da * da + dy * dy + dc * dc);
    }

    /** +1 if the point is on the high side of the plane across the axis, -1 if not. */
    public static int side(VaultPortalShape shape, double x, double z) {
        double across = shape.axis() == Axis.X ? z : x;
        return across >= plane(shape) ? 1 : -1;
    }

    /**
     * The side the front faces. The door model's front is south on axis X and, turned by y 90, west on axis Z. Either
     * way a viewer there has column 0 on their left.
     */
    public static int frontSide(Axis axis) {
        return axis == Axis.X ? 1 : -1;
    }

    /** Quadratic ease-out, clamped to 0 to 1: quick off the latch, slowing into the stop. */
    public static double easeOut(double t) {
        double clamped = Math.max(0, Math.min(1, t));
        double rest = 1 - clamped;
        return 1 - rest * rest;
    }

    /**
     * A leaf at {@code degrees} open toward {@code side}. It turns about the edge of its face on the player's side, at the
     * jamb, so at 90 degrees it stands against the jamb's inner face and its thickness lies inside the opening.
     */
    public static LeafFrame leaf(VaultPortalShape shape, Leaf leaf, int side, double degrees) {
        double theta = Math.toRadians(degrees);
        double phi = leaf.direction * side * theta;
        double hingeAlong = firstAlong(shape) + (leaf == Leaf.LEFT ? 0 : shape.width());
        double hingeAcross = plane(shape) + side * leafThickness() / 2;
        return new LeafFrame(hingeAlong, hingeAcross,
                leaf.direction * Math.cos(theta), side * Math.sin(theta),
                side * Math.sin(phi), -side * Math.cos(phi));
    }

    /** The leaf's pieces, one per column it covers, in rising closed coordinates. */
    public static List<Segment> segments(Leaf leaf, int width) {
        double low = leaf == Leaf.LEFT ? 0 : leafLength(width);
        double high = leaf == Leaf.LEFT ? leafLength(width) : width;
        List<Segment> segments = new ArrayList<>();
        for (int column = (int) Math.floor(low); column < high; column++) {
            segments.add(new Segment(Math.max(column, low), Math.min(column + 1, high), column));
        }
        return List.copyOf(segments);
    }

    /** How far from its hinge a leaf reaches at closed coordinate {@code x}. */
    public static double fromHinge(Leaf leaf, double x, int width) {
        return leaf == Leaf.LEFT ? x : width - x;
    }

    /** What the front, or the back, of a segment shows in {@code row}, counted up from the sill. */
    public static Tile tile(boolean front, Segment segment, int row, int width, int height) {
        int column = front ? segment.column() : width - 1 - segment.column();
        DoorPart part = DoorPart.of(column, row, width, height);
        return new Tile(part, u(part, imageX(front, segment.from(), width) - column),
                u(part, imageX(front, segment.to(), width) - column));
    }

    /** The left edge of the 4 by 4 gem, centred on the seam whatever the width, in closed coordinates. */
    public static double gemFrom(int width) {
        return leafLength(width) - GEM_PX / 2.0 * PIXEL;
    }

    public static double gemTo(int width) {
        return leafLength(width) + GEM_PX / 2.0 * PIXEL;
    }

    /** Above the sill, in blocks. */
    public static double gemTop() {
        return DoorPart.SEAT_ROW + 1 - GEM_TOP_ROW_PX * PIXEL;
    }

    public static double gemBottom() {
        return gemTop() - GEM_PX * PIXEL;
    }

    /** The gem's texture u, 0 to 1, at closed coordinate {@code x}. The gem is mirrored on the back like the door. */
    public static double gemU(boolean front, double x, int width) {
        return (imageX(front, x, width) - gemFrom(width)) / (GEM_PX * PIXEL);
    }

    /**
     * Whether this opening gets a door. The return portal has none (§3.2): a player arrives 3 blocks from it, inside the
     * open distance, and it is built lit, so it is never sealed.
     */
    public static boolean hasDoor(boolean inVault, VaultPortalShape shape) {
        boolean returnPortal = shape.axis() == Axis.X
                && shape.minX() == ReturnPortal.FIRST_X + 1
                && shape.minZ() == ReturnPortal.PLANE_Z
                && shape.width() == ReturnPortal.WIDTH - 2
                && shape.height() == ReturnPortal.HEIGHT - 2;
        return !(inVault && returnPortal);
    }

    /**
     * Vanilla's face shading, 1 up, 0.5 down, 0.8 north and south, 0.6 east and west, weighted by the squared normal for a
     * face between them, so a swinging leaf darkens smoothly. The door is shaded as normal (§3.2).
     */
    public static double shade(double nx, double ny, double nz) {
        double vertical = ny > 0 ? 1.0 : 0.5;
        return nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * vertical;
    }

    private static double imageX(boolean front, double x, int width) {
        return front ? x : width - x;
    }

    private static double u(DoorPart part, double local) {
        return part.mirrored() ? 1 - local : local;
    }

    private static double firstAlong(VaultPortalShape shape) {
        return shape.axis() == Axis.X ? shape.minX() : shape.minZ();
    }

    /** The middle of the cells, across the axis. */
    private static double plane(VaultPortalShape shape) {
        return (shape.axis() == Axis.X ? shape.minZ() : shape.minX()) + 0.5;
    }

    private static double outside(double value, double low, double high) {
        return value < low ? low - value : value > high ? value - high : 0;
    }
}
