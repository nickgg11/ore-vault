package com.orevault.orevault.core.frame;

import com.orevault.orevault.core.frame.FrameNeighbours.Face;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The 12 neighbour states that round off a Vault Frame's outer corner (§3.2): exactly two connections, on two
 * perpendicular axes. Each is the top-left corner of a ring seen from the south, {@link #ED}, turned to fit, so the
 * curve is written once. The block's shape and the generated models both read these boxes, which is what keeps the
 * outline, the collision and the picture the same object.
 *
 * <p>The tread faces where the curve leaves one arm and the riser where it reaches the other; they carry the rim
 * textures, which are laid out in the top-left corner's coordinates. Each face's UV is solved so its four corners land
 * on those coordinates through vanilla's vertex order ({@code FaceInfo}, {@code CuboidFace.UVs}, {@code Quadrant}),
 * which is why {@link #VERTICES} repeats that table: nothing in the client can be imported here.
 */
public enum FrameCorner {
    ED(Face.EAST, Face.UP, Face.SOUTH, false),
    WD(Face.WEST, Face.UP, Face.SOUTH, true),
    EU(Face.EAST, Face.DOWN, Face.NORTH, true),
    WU(Face.WEST, Face.DOWN, Face.NORTH, false),
    SD(Face.SOUTH, Face.UP, Face.EAST, true),
    ND(Face.NORTH, Face.UP, Face.EAST, false),
    SU(Face.SOUTH, Face.DOWN, Face.EAST, true),
    NU(Face.NORTH, Face.DOWN, Face.EAST, false),
    NE(Face.NORTH, Face.WEST, Face.DOWN, false),
    NW(Face.NORTH, Face.EAST, Face.DOWN, false),
    ES(Face.SOUTH, Face.WEST, Face.DOWN, false),
    SW(Face.SOUTH, Face.EAST, Face.DOWN, false);

    private static final int FULL = 16;

    /**
     * The curve, top row first: inset from the left, bottom, top. The Designer read it off {@code vault_frame_dr.png},
     * where the outer rim is the first highlight in each row, so a redraw of that curve changes these too.
     */
    private static final int[][] ROWS = {
        {13, 15, 16}, {10, 14, 15}, {8, 13, 14}, {7, 12, 13}, {6, 11, 12}, {5, 10, 11}, {4, 9, 10}, {3, 8, 9}, {2, 6, 8},
        {1, 3, 6}, {0, 0, 3},
    };

    /** {@code FaceInfo}'s four vertices of each face, as bits: 1 is max x, 2 max y, 4 max z. */
    private static final Map<Face, int[]> VERTICES = Map.of(
            Face.DOWN, new int[] {4, 0, 1, 5},
            Face.UP, new int[] {2, 6, 7, 3},
            Face.NORTH, new int[] {3, 1, 0, 2},
            Face.SOUTH, new int[] {6, 4, 5, 7},
            Face.WEST, new int[] {2, 0, 4, 6},
            Face.EAST, new int[] {7, 5, 1, 3});

    private static final int[] ROTATIONS = {0, 90, 270, 180};
    private static final int QUARTER = 90;
    private static final int VERTEX_COUNT = 4;

    private static final Map<FrameNeighbours, FrameCorner> BY_NEIGHBOURS =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(FrameCorner::neighbours, Function.identity()));

    private final Face mapX;
    private final Face mapY;
    private final Face mapZ;
    private final boolean mixedRim;

    /** Where the top-left corner's +x, +y and +z go in this state, and whether its bars disagree about the lit edge. */
    FrameCorner(Face mapX, Face mapY, Face mapZ, boolean mixedRim) {
        this.mapX = mapX;
        this.mapY = mapY;
        this.mapZ = mapZ;
        this.mixedRim = mixedRim;
    }

    /** What a surface of the corner is drawn with. */
    public enum Surface {
        /** A face in the plane of the ring, with the texture and default UV a cube in this state would show. */
        PLANE,
        /** A step's flat, carrying the tread texture. */
        TREAD,
        /** A step's rise, carrying the riser texture. */
        RISER
    }

    /** A box in model units, 0 to 16. */
    public record Box(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
    }

    /** One face of a box. {@code culled} faces lie on the block's boundary and hide against a full neighbour. */
    public record ElementFace(Face face, Surface surface, int u0, int v0, int u1, int v1, int rotation, boolean culled) {
    }

    /** One box and the faces of it that can be seen; the rest sit against an arm or another box. */
    public record Element(Box box, List<ElementFace> faces) {
    }

    /** The corner these neighbours make, or empty when the block stays a cube. */
    public static Optional<FrameCorner> of(FrameNeighbours neighbours) {
        return Optional.ofNullable(BY_NEIGHBOURS.get(neighbours));
    }

    /** The two sides that connect. */
    public List<Face> arms() {
        return List.of(mapX, opposite(mapY));
    }

    public FrameNeighbours neighbours() {
        Set<Face> arms = EnumSet.copyOf(arms());
        return new FrameNeighbours(arms.contains(Face.NORTH), arms.contains(Face.EAST), arms.contains(Face.SOUTH),
                arms.contains(Face.WEST), arms.contains(Face.UP), arms.contains(Face.DOWN));
    }

    public Face tread() {
        return mapY;
    }

    public Face riser() {
        return opposite(mapX);
    }

    /** True when the riser's bar has its lit edge on the other side from the tread's, so the rim has to cross over. */
    public boolean mixedRim() {
        return mixedRim;
    }

    /** The part of the tread texture's name after {@code vault_frame}. */
    public String treadTexture() {
        return "_rim_tread" + (mixedRim ? "_mixed" : "");
    }

    /** The part of the riser texture's name after {@code vault_frame}. */
    public String riserTexture() {
        return "_rim_riser" + (mixedRim ? "_mixed" : "");
    }

    /** The boxes running down the curve, row by row from the tread's end. */
    public List<Element> elements() {
        List<Element> elements = new ArrayList<>(ROWS.length);
        for (int row = 0; row < ROWS.length; row++) {
            elements.add(element(row));
        }
        return List.copyOf(elements);
    }

    private Element element(int row) {
        int inset = ROWS[row][0];
        int[] first = toState(inset, ROWS[row][1], 0);
        int[] second = toState(FULL, ROWS[row][2], FULL);
        Box box = new Box(Math.min(first[0], second[0]), Math.min(first[1], second[1]), Math.min(first[2], second[2]),
                Math.max(first[0], second[0]), Math.max(first[1], second[1]), Math.max(first[2], second[2]));
        return new Element(box, List.of(
                planeFace(box, mapZ),
                planeFace(box, opposite(mapZ)),
                rimFace(box, tread(), Surface.TREAD, row == 0),
                rimFace(box, riser(), Surface.RISER, inset == 0)));
    }

    private static ElementFace planeFace(Box box, Face face) {
        int[] uv = defaultUv(box, face);
        return new ElementFace(face, Surface.PLANE, uv[0], uv[1], uv[2], uv[3], 0, true);
    }

    private ElementFace rimFace(Box box, Face face, Surface surface, boolean culled) {
        int[][] wanted = new int[VERTEX_COUNT][];
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            int[] canonical = toCanonical(vertexPosition(box, VERTICES.get(face)[vertex]));
            // The tread's column is position along the arm and its row depth; the riser's row is position, its column depth.
            wanted[vertex] = surface == Surface.TREAD
                    ? new int[] {canonical[0], canonical[2]}
                    : new int[] {canonical[2], FULL - canonical[1]};
        }
        return solve(wanted, face, surface, culled);
    }

    /** The UV and rotation that put each vertex on its wanted point, preferring no rotation, then no flip. */
    private static ElementFace solve(int[][] wanted, Face face, Surface surface, boolean culled) {
        int minU = FULL;
        int maxU = 0;
        int minV = FULL;
        int maxV = 0;
        for (int[] point : wanted) {
            minU = Math.min(minU, point[0]);
            maxU = Math.max(maxU, point[0]);
            minV = Math.min(minV, point[1]);
            maxV = Math.max(maxV, point[1]);
        }
        for (int rotation : ROTATIONS) {
            for (int[] us : new int[][] {{minU, maxU}, {maxU, minU}}) {
                for (int[] vs : new int[][] {{minV, maxV}, {maxV, minV}}) {
                    if (lands(wanted, us, vs, rotation)) {
                        return new ElementFace(face, surface, us[0], vs[0], us[1], vs[1], rotation, culled);
                    }
                }
            }
        }
        throw new IllegalStateException("no UV puts " + face + " on its texture");
    }

    /** {@code CuboidFace.UVs#getVertexU} and {@code getVertexV} after {@code Quadrant#rotateVertexIndex}. */
    private static boolean lands(int[][] wanted, int[] us, int[] vs, int rotation) {
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            int rotated = (vertex + rotation / QUARTER) % VERTEX_COUNT;
            int u = rotated == 0 || rotated == 1 ? us[0] : us[1];
            int v = rotated == 0 || rotated == VERTEX_COUNT - 1 ? vs[0] : vs[1];
            if (u != wanted[vertex][0] || v != wanted[vertex][1]) {
                return false;
            }
        }
        return true;
    }

    /** {@code FaceBakery#defaultFaceUV}. */
    private static int[] defaultUv(Box box, Face face) {
        return switch (face) {
            case DOWN -> new int[] {box.fromX(), FULL - box.toZ(), box.toX(), FULL - box.fromZ()};
            case UP -> new int[] {box.fromX(), box.fromZ(), box.toX(), box.toZ()};
            case NORTH -> new int[] {FULL - box.toX(), FULL - box.toY(), FULL - box.fromX(), FULL - box.fromY()};
            case SOUTH -> new int[] {box.fromX(), FULL - box.toY(), box.toX(), FULL - box.fromY()};
            case WEST -> new int[] {box.fromZ(), FULL - box.toY(), box.toZ(), FULL - box.fromY()};
            case EAST -> new int[] {FULL - box.toZ(), FULL - box.toY(), FULL - box.fromZ(), FULL - box.fromY()};
        };
    }

    private static int[] vertexPosition(Box box, int corner) {
        return new int[] {
            (corner & 1) != 0 ? box.toX() : box.fromX(),
            (corner & 2) != 0 ? box.toY() : box.fromY(),
            (corner & 4) != 0 ? box.toZ() : box.fromZ(),
        };
    }

    private int[] toState(int x, int y, int z) {
        int[] state = new int[3];
        state[axis(mapX)] = positive(mapX) ? x : FULL - x;
        state[axis(mapY)] = positive(mapY) ? y : FULL - y;
        state[axis(mapZ)] = positive(mapZ) ? z : FULL - z;
        return state;
    }

    private int[] toCanonical(int[] state) {
        return new int[] {
            positive(mapX) ? state[axis(mapX)] : FULL - state[axis(mapX)],
            positive(mapY) ? state[axis(mapY)] : FULL - state[axis(mapY)],
            positive(mapZ) ? state[axis(mapZ)] : FULL - state[axis(mapZ)],
        };
    }

    private static int axis(Face face) {
        return switch (face) {
            case EAST, WEST -> 0;
            case UP, DOWN -> 1;
            case NORTH, SOUTH -> 2;
        };
    }

    private static boolean positive(Face face) {
        return face == Face.EAST || face == Face.UP || face == Face.SOUTH;
    }

    private static Face opposite(Face face) {
        return switch (face) {
            case NORTH -> Face.SOUTH;
            case SOUTH -> Face.NORTH;
            case EAST -> Face.WEST;
            case WEST -> Face.EAST;
            case UP -> Face.DOWN;
            case DOWN -> Face.UP;
        };
    }
}
