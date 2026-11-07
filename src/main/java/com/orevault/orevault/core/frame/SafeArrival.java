package com.orevault.orevault.core.frame;

/**
 * Where a player's feet go on arriving in a Vault (§3.2, old #77). Arrival is the team anchor column, X=0, Z=0, never a
 * coordinate taken from the Overworld; this only decides the height in that column, so a player who has built over the
 * anchor lands on top of it, or in the room under a roof, rather than inside it.
 */
public final class SafeArrival {
    /** The grass surface of both layer stacks is Y=250 (§3.1), so the anchor's feet stand at Y=251. */
    public static final int DEFAULT_FEET_Y = 251;

    private SafeArrival() {
    }

    /**
     * What a height of the column is to a player. {@link #OPEN} is anything with no collision that holds no fluid: air, a
     * torch, a flower, carpet, a rail, a pressure plate or a button. A block a player can stand in is no reason to look
     * further down, and a fluid is neither room nor floor.
     */
    public enum Space {
        OPEN,
        SOLID,
        FLUID
    }

    @FunctionalInterface
    public interface Column {
        Space at(int y);
    }

    /**
     * The highest height at or below the surface open at the feet and the head, with a solid block under the feet. The
     * floor test is what keeps a stale or over-high surface from landing a player in mid-air: the search goes on down the
     * air to whatever it stands on. Nothing above {@code maxY} or below {@code minY} is read.
     *
     * @param surface the {@code MOTION_BLOCKING} heightmap of the column: the lowest height above every block that blocks
     *     motion
     * @param minY the level's lowest block
     * @param maxY the level's highest block
     * @return the feet's height, or {@link #DEFAULT_FEET_Y} when the column has no such pocket
     */
    public static int feetY(int surface, int minY, int maxY, Column column) {
        for (int y = Math.min(surface, maxY - 1); y > minY; y--) {
            if (column.at(y) == Space.OPEN && column.at(y + 1) == Space.OPEN && column.at(y - 1) == Space.SOLID) {
                return y;
            }
        }
        return DEFAULT_FEET_Y;
    }
}
