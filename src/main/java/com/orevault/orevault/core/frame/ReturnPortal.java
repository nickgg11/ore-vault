package com.orevault.orevault.core.frame;

import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import java.util.ArrayList;
import java.util.List;

/**
 * The team's one return portal (§3.2 Teleportation Logic): a single-plane 4×5 frame with a 2×3 opening, standing on the
 * ground three blocks south of the team anchor (X=0, Z=0), so a player arriving at the anchor faces it and is not inside
 * it. Its place in X and Z is fixed and its axis is always X, so there is never a second one: the first build took the
 * axis from the portal the player came through and the X and Z from the Overworld, and filled a Vault with them (old #98).
 *
 * <p>Nothing about it is stored. Its height is found again on every entry, and its tier is read back from its cells and
 * only ever raised, by the rule a signet pressed into it follows, {@code SignetTier#mayPress} (old #109; §3.2 Pressing a
 * Signet). That rule arrives as {@link Entering}, because a pure {@code core} package imports no other. A plan lists the cells before the frames, and the caller writes them in that order: the last frame block to
 * arrive would otherwise find an empty ring and seal it with a door (ADR 0010).
 */
public final class ReturnPortal {
    /** The plane, three blocks south of the anchor. */
    public static final int PLANE_Z = 3;
    /** The west column of the frame; the opening is X=0 and X=1. */
    public static final int FIRST_X = -1;
    public static final int WIDTH = 4;
    public static final int HEIGHT = 5;

    private ReturnPortal() {
    }

    /**
     * The world as the builder reads it. {@link Kind#PORTAL} is anything in {@code orevault:vault_openings} on axis X, a door
     * included; an opening on the other axis is {@link Kind#OTHER}. {@link Kind#AIR} is anything a block may replace.
     */
    public interface Blocks {
        Kind kindAt(int x, int y, int z);

        /** The rank of the portal here, from 1; 0 for a door or anything that is not a portal. */
        int rankAt(int x, int y, int z);
    }

    /** What a position of the ring is. Corners are optional (§3.2): one that is occupied is left as it is. */
    public enum Role {
        SIDE,
        CORNER,
        CELL
    }

    public record Place(Cell cell, Role role) {
    }

    /** The signet tier of the portal the player came through, as {@code SignetTier} answers for itself. */
    public interface Entering {
        /** From 1. */
        int rank();

        /** Whether this tier raises a portal of that rank, 0 for none standing. */
        boolean mayPress(int portalRank);
    }

    /**
     * What to write, cells first.
     *
     * @param blocked something other than air, a frame or an opening is on a side or in the opening, so nothing is written
     * @param rank the tier the cells have once it is written: the highest of the standing cells and the entering signet's
     */
    public record Plan(boolean blocked, int baseY, int rank, List<Cell> cells, List<Cell> frames) {
        public boolean changesNothing() {
            return !blocked && cells.isEmpty() && frames.isEmpty();
        }
    }

    /** The 20 positions of the ring whose bottom row is {@code baseY}. */
    public static List<Place> layout(int baseY) {
        List<Place> places = new ArrayList<>();
        for (int column = 0; column < WIDTH; column++) {
            for (int row = 0; row < HEIGHT; row++) {
                boolean edgeColumn = column == 0 || column == WIDTH - 1;
                boolean edgeRow = row == 0 || row == HEIGHT - 1;
                Role role = edgeColumn && edgeRow ? Role.CORNER : edgeColumn || edgeRow ? Role.SIDE : Role.CELL;
                places.add(new Place(new Cell(FIRST_X + column, baseY + row, PLANE_Z), role));
            }
        }
        return List.copyOf(places);
    }

    /**
     * The bottom row of the ring: where a standing one is, or else one above the ground under its four columns. Where any
     * column still holds frame blocks, the ground is read only in those columns, below the lowest frame of each, so a
     * portal whose cells dissolved is rebuilt on its own frames, not on top of them or on a roof built over them. A roof
     * over a standing portal does not move it either.
     *
     * @param top the highest height worth reading: the highest {@code MOTION_BLOCKING} heightmap of the four columns
     * @param minY the level's lowest block
     */
    public static int baseY(Blocks blocks, int top, int minY) {
        for (int y = top; y > minY; y--) {
            if (blocks.kindAt(0, y, PLANE_Z) == Kind.PORTAL && blocks.kindAt(0, y - 1, PLANE_Z) == Kind.FRAME
                    && stands(blocks, y - 1)) {
                return y - 1;
            }
        }
        int framed = minY - 1;
        int open = minY;
        for (int x = FIRST_X; x < FIRST_X + WIDTH; x++) {
            int lowestFrame = Integer.MAX_VALUE;
            for (int y = top; y >= minY; y--) {
                if (blocks.kindAt(x, y, PLANE_Z) == Kind.FRAME) {
                    lowestFrame = y;
                }
            }
            if (lowestFrame != Integer.MAX_VALUE) {
                framed = Math.max(framed, groundFrom(blocks, x, lowestFrame - 1, minY));
            } else {
                open = Math.max(open, groundFrom(blocks, x, top, minY));
            }
        }
        return (framed >= minY ? framed : open) + 1;
    }

    /** The first block that is neither air, frame nor opening, at or below {@code from}; {@code minY} if there is none. */
    private static int groundFrom(Blocks blocks, int x, int from, int minY) {
        for (int y = from; y >= minY; y--) {
            if (blocks.kindAt(x, y, PLANE_Z) == Kind.OTHER) {
                return y;
            }
        }
        return minY;
    }

    /** Whether the sides are all frame and the opening all filled: a ring that works, with or without its corners. */
    private static boolean stands(Blocks blocks, int baseY) {
        for (Place place : layout(baseY)) {
            Kind kind = kindAt(blocks, place.cell());
            if (place.role() == Role.SIDE && kind != Kind.FRAME || place.role() == Role.CELL && kind != Kind.PORTAL) {
                return false;
            }
        }
        return true;
    }

    /**
     * What makes the ring at {@code baseY} whole at the right tier.
     *
     * @param entering the signet tier of the portal the player came through; the lowest when there was none
     */
    public static Plan plan(Blocks blocks, int baseY, Entering entering) {
        List<Place> layout = layout(baseY);
        int highest = 0;
        for (Place place : layout) {
            Kind kind = kindAt(blocks, place.cell());
            boolean fits = switch (place.role()) {
                case SIDE -> kind == Kind.FRAME || kind == Kind.AIR;
                case CORNER -> true;
                case CELL -> kind == Kind.PORTAL || kind == Kind.AIR;
            };
            if (!fits) {
                return new Plan(true, baseY, 0, List.of(), List.of());
            }
            if (place.role() == Role.CELL && kind == Kind.PORTAL) {
                highest = Math.max(highest, blocks.rankAt(place.cell().x(), place.cell().y(), place.cell().z()));
            }
        }
        int rank = entering.mayPress(highest) ? entering.rank() : highest;
        List<Cell> cells = new ArrayList<>();
        List<Cell> frames = new ArrayList<>();
        for (Place place : layout) {
            Cell cell = place.cell();
            Kind kind = kindAt(blocks, cell);
            if (place.role() == Role.CELL) {
                if (kind != Kind.PORTAL || blocks.rankAt(cell.x(), cell.y(), cell.z()) != rank) {
                    cells.add(cell);
                }
            } else if (kind == Kind.AIR) {
                frames.add(cell);
            }
        }
        return new Plan(false, baseY, rank, List.copyOf(cells), List.copyOf(frames));
    }

    private static Kind kindAt(Blocks blocks, Cell cell) {
        return blocks.kindAt(cell.x(), cell.y(), cell.z());
    }
}
