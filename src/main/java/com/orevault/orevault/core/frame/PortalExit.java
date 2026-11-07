package com.orevault.orevault.core.frame;

import java.util.function.IntPredicate;

/**
 * Where the return position goes (§3.2): out of the portal on the side the player came in from, past the last portal block,
 * so coming home never stands them in the portal again. The portal is a plane one block thick, but two rings can sit face
 * to face, so the walk crosses up to {@link #MAX_PORTAL_CELLS} cells along the plane's normal.
 */
public final class PortalExit {
    /** Cells of portal the walk crosses before it stops looking. */
    public static final int MAX_PORTAL_CELLS = 4;

    /** Below this, a speed along the normal is standing still, and the facing decides. */
    private static final double STILL = 0.01;

    private PortalExit() {
    }

    /**
     * The side of the plane the player came from: -1 or +1 along the normal. That is behind their direction of travel, or
     * behind the way they face when they walked in too slowly to tell.
     *
     * @param speedAlongNormal the player's speed along the normal
     * @param facingAlongNormal -1, 0 or +1: the normal's component of the horizontal direction they face
     */
    public static int side(double speedAlongNormal, int facingAlongNormal) {
        if (Math.abs(speedAlongNormal) > STILL) {
            return speedAlongNormal > 0 ? -1 : 1;
        }
        return facingAlongNormal > 0 ? -1 : 1;
    }

    /**
     * How many cells from the one the player stood in to the first that is not portal, along {@link #side}.
     *
     * @param portalAt whether the cell this many steps out is portal
     * @return from 1, when the next cell is clear, to {@link #MAX_PORTAL_CELLS} + 1
     */
    public static int stepsOut(IntPredicate portalAt) {
        int steps = 1;
        while (steps <= MAX_PORTAL_CELLS && portalAt.test(steps)) {
            steps++;
        }
        return steps;
    }
}
