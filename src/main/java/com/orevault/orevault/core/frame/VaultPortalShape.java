package com.orevault.orevault.core.frame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One valid portal opening, and the scan that finds it (§3.2): 2 to 21 wide, 3 to 21 tall, with all four sides of
 * Vault Frame and nothing but air or portal inside. The four corner positions are not looked at: they may hold
 * anything, so a ring without them is valid and costs four frames less.
 *
 * <p>The scan measures the opening and never the bars, the reverse of the first build's scanner. A bar's run says
 * nothing about the opening: with corners empty it stops short, and with a neighbouring portal sharing it, or a wall of
 * frame behind it, it runs on past the corner. Walking the open cells from just inside the clicked block finds the
 * opening whichever bar was clicked, and whatever stands beyond the corners.
 *
 * <p>This is the whole scan for both of its callers: a signet pressed into a frame (E3-S4, #229) and a frame block
 * placed, which seals a completed ring with a door (Nick, {@code DESIGN_LOG.md}, 2026-11-01). Neither knows which part
 * of the ring it started from, so any frame block will do, the corners included. A placed block can complete two
 * frames at once, so {@link #findAll} returns all of them.
 *
 * @param minX the opening's lowest cell, as {@link #minY} and {@link #minZ} are
 * @param width the opening's size along {@link #axis}
 * @param height the opening's size up the y axis
 */
public record VaultPortalShape(Axis axis, int minX, int minY, int minZ, int width, int height) {
    public static final int MIN_WIDTH = 2;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_SIZE = 21;

    /** The furthest one open cell can be from another in a valid opening; a run stops at {@code MAX_STEPS + 1}. */
    public static final int MAX_STEPS = MAX_SIZE - 1;

    /**
     * Where a ring block's opening can start. The diagonals are enough: a corner reaches its opening no other way, and
     * since an opening is at least 2 by 3, every side block has one of them inside it too.
     */
    private static final int[][] INSIDE = {{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    /** The horizontal axis a frame runs along, named as Minecraft's {@code Direction.Axis} names it. */
    public enum Axis {
        X,
        Z;

        private int along(int x, int z) {
            return this == X ? x : z;
        }

        private int across(int x, int z) {
            return this == X ? z : x;
        }

        private Cell cell(int along, int y, int across) {
            return this == X ? new Cell(along, y, across) : new Cell(across, y, along);
        }
    }

    /** What a position is to the scan. Anything that is not one of the first three is {@link #OTHER}. */
    public enum Kind {
        /** The Vault Frame block, in any of its states. See {@link View}. */
        FRAME,
        /** Something that already fills an opening, which a scan accepts inside it (§3.2). */
        PORTAL,
        AIR,
        OTHER
    }

    /** A block position. */
    public record Cell(int x, int y, int z) {
    }

    /**
     * The world as the scan sees it. A real adapter reads the block and nothing about its form:
     *
     * <pre>{@code
     * if (state.is(ModBlocks.VAULT_FRAME)) return Kind.FRAME;
     * if (state.isAir()) return Kind.AIR;
     * return state.is(<the portal tag>) ? Kind.PORTAL : Kind.OTHER;
     * }</pre>
     *
     * <p>{@link Kind#FRAME} means the position holds the Vault Frame block, and it must be decided by asking exactly
     * that. Never by shape, solidity, collision or face sturdiness ({@code isCollisionShapeFullBlock},
     * {@code isSolidRender}, {@code isFaceSturdy} and the like), and never by the frame's own neighbour properties: a
     * rounded corner and a bar's end are not full cubes (§3.2, {@link FrameCorner}), and a test of shape would turn a
     * correct ring into a refused one at the block where it is rounded.
     *
     * <p>Whatever fills an opening has to answer {@link Kind#PORTAL}, the sealed door as well as the four portals, or
     * pressing a signet into a door would find its own door in the way.
     */
    @FunctionalInterface
    public interface View {
        Kind kindAt(int x, int y, int z);
    }

    /** The first opening {@link #findAll} finds from this block. */
    public static Optional<VaultPortalShape> find(View view, int x, int y, int z) {
        return findAll(view, x, y, z).stream().findFirst();
    }

    /**
     * Every opening this block frames, X axis first, and on each axis one above the block before one below it.
     * Empty unless the block is a frame. Two openings come back only when they share a bar, or a block on two axes.
     */
    public static List<VaultPortalShape> findAll(View view, int x, int y, int z) {
        if (view.kindAt(x, y, z) != Kind.FRAME) {
            return List.of();
        }
        Set<VaultPortalShape> found = new LinkedHashSet<>();
        for (Axis axis : Axis.values()) {
            Plane plane = new Plane(view, axis, axis.across(x, z));
            int along = axis.along(x, z);
            for (int[] inside : INSIDE) {
                plane.openingAround(along + inside[0], y + inside[1]).ifPresent(found::add);
            }
        }
        return List.copyOf(found);
    }

    /** The opening's cells, bottom row first. */
    public List<Cell> cells() {
        int firstAlong = axis.along(minX, minZ);
        int across = axis.across(minX, minZ);
        List<Cell> cells = new ArrayList<>(width * height);
        for (int y = minY; y < minY + height; y++) {
            for (int along = firstAlong; along < firstAlong + width; along++) {
                cells.add(axis.cell(along, y, across));
            }
        }
        return cells;
    }

    /** One vertical plane of the world, addressed as a distance along it and a height. */
    private record Plane(View view, Axis axis, int across) {
        private Kind kind(int along, int y) {
            Cell cell = axis.cell(along, y, across);
            return view.kindAt(cell.x(), cell.y(), cell.z());
        }

        private boolean open(int along, int y) {
            Kind kind = kind(along, y);
            return kind == Kind.AIR || kind == Kind.PORTAL;
        }

        /** Open cells from this one to the first that is not, stopping at {@code MAX_STEPS + 1}: too long to be valid. */
        private int run(int along, int y, int stepAlong, int stepUp) {
            int steps = 0;
            while (steps <= MAX_STEPS && open(along + stepAlong * (steps + 1), y + stepUp * (steps + 1))) {
                steps++;
            }
            return steps;
        }

        /** The valid opening holding this cell, if it has one. The walk only proposes; {@link #valid} decides. */
        private Optional<VaultPortalShape> openingAround(int along, int y) {
            if (!open(along, y)) {
                return Optional.empty();
            }
            int left = run(along, y, -1, 0);
            int down = run(along, y, 0, -1);
            int width = left + run(along, y, 1, 0) + 1;
            int height = down + run(along, y, 0, 1) + 1;
            if (width < MIN_WIDTH || width > MAX_SIZE || height < MIN_HEIGHT || height > MAX_SIZE) {
                return Optional.empty();
            }
            int firstAlong = along - left;
            int bottom = y - down;
            if (!valid(firstAlong, bottom, width, height)) {
                return Optional.empty();
            }
            Cell first = axis.cell(firstAlong, bottom, across);
            return Optional.of(new VaultPortalShape(axis, first.x(), first.y(), first.z(), width, height));
        }

        /** The four sides, corners left out, and every cell inside. */
        private boolean valid(int firstAlong, int bottom, int width, int height) {
            for (int along = firstAlong; along < firstAlong + width; along++) {
                if (kind(along, bottom - 1) != Kind.FRAME || kind(along, bottom + height) != Kind.FRAME) {
                    return false;
                }
            }
            for (int y = bottom; y < bottom + height; y++) {
                if (kind(firstAlong - 1, y) != Kind.FRAME || kind(firstAlong + width, y) != Kind.FRAME) {
                    return false;
                }
                for (int along = firstAlong; along < firstAlong + width; along++) {
                    if (!open(along, y)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
