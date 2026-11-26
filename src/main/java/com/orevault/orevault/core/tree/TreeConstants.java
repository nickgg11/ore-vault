package com.orevault.orevault.core.tree;

/**
 * The tree's constants that are not layout (§11 Tree Layout Data). The level curve (E5-S1, #240) is the first reader of
 * the level cap and points; E8a-S1 (#208) adds the refund prices and the windows of §4.4. Choosing a price from a node's
 * class is the refund code's (#230), not here.
 */
public final class TreeConstants {
    /** The level cap of both trees (§4.3 step 1). */
    public static final int LEVEL_CAP = 30;
    /**
     * Points a level awards (§4.3 step 2). Pinned, not derived from the loaded node count (Nick, 2026-10-26): 150 points
     * at level 30.
     */
    public static final int POINTS_PER_LEVEL = 5;

    /** Vanilla XP levels to refund any node, travel included (§4.4). */
    public static final int REFUND_XP_PER_NODE = 3;
    /** Vanilla XP levels to refund a pact (§4.4). */
    public static final int REFUND_XP_PACT = 12;
    /** Vanilla XP levels to refund a keystone (§4.4). */
    public static final int REFUND_XP_KEYSTONE = 30;

    /**
     * The free undo window after a buy, 60 seconds (§4.4). Session state, never saved (§4.4), but in game ticks like the
     * respec window so both are measured on one clock.
     */
    public static final int UNDO_WINDOW_TICKS = 60 * 20;
    /**
     * The free respec window after a reset, 10 minutes (§4.4). In game ticks because {@code free_respec_until} is saved as
     * game time (ADR 0005).
     */
    public static final int FREE_RESPEC_WINDOW_TICKS = 10 * 60 * 20;

    private TreeConstants() {
    }
}
