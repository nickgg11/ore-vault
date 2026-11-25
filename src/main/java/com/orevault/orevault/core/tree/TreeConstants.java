package com.orevault.orevault.core.tree;

/**
 * The tree's constants that are not layout (§11 Tree Layout Data). The level curve (E5-S1, #240) is their first reader, so
 * the class starts with the two it needs; E8a-S1 (#208) adds the refund prices and the windows beside them.
 */
public final class TreeConstants {
    /** The level cap of both trees (§4.3 step 1). */
    public static final int LEVEL_CAP = 30;
    /**
     * Points a level awards (§4.3 step 2). Pinned, not derived from the loaded node count (Nick, 2026-10-26): 150 points
     * at level 30.
     */
    public static final int POINTS_PER_LEVEL = 5;

    private TreeConstants() {
    }
}
