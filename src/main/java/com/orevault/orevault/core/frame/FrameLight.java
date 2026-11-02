package com.orevault.orevault.core.frame;

import com.orevault.orevault.core.frame.VaultPortalShape.Axis;

/**
 * What colour a frame block shows (§3.2 Frame colour). The colour is not stored: a frame block shows the highest tier
 * among the portal cells around it in their plane, worked out from the world each time the section is drawn (ADR 0010).
 * A tier is 1 to 4, and 0 is grey. Corners, shared bars and dissolves all follow from that one rule.
 */
public final class FrameLight {
    /** The tint of a dormant frame's strands, the item included. */
    public static final int DORMANT = 0xFF74767C;

    private static final int NEAREST = 1;

    /** The tier ladder (§3.2), T1 first. */
    private static final int[] PORTAL_COLORS = {0xFF0D7026, 0xFF316CFF, 0xFFA038D0, 0xFFFF8000};

    /** Frame runes use the ladder but for Tier 1 (ADR 0010 Amendment): the darker green is lost in the bezel. */
    private static final int RUNE_TIER_ONE = 0xFF148031;

    private FrameLight() {
    }

    /** The world as this rule reads it. */
    @FunctionalInterface
    public interface PortalView {
        /** The tier of a portal at this position whose own axis is this one, and 0 for anything else. */
        int tierAt(int x, int y, int z, Axis axis);
    }

    /** The highest tier around this block, or 0 for grey: 8 cells on each axis's plane, 14 blocks in all. */
    public static int tierAround(int x, int y, int z, PortalView view) {
        int highest = 0;
        for (int along = -NEAREST; along <= NEAREST; along++) {
            for (int up = -NEAREST; up <= NEAREST; up++) {
                if (along != 0 || up != 0) {
                    highest = Math.max(highest, view.tierAt(x + along, y + up, z, Axis.X));
                    highest = Math.max(highest, view.tierAt(x, y + up, z + along, Axis.Z));
                }
            }
        }
        return highest;
    }

    /** The colour of a frame's strands at this tier. */
    public static int runeColor(int tier) {
        if (tier == 0) {
            return DORMANT;
        }
        return tier == 1 ? RUNE_TIER_ONE : portalColor(tier);
    }

    /** The colour of the portal strip at this tier. */
    public static int portalColor(int tier) {
        return PORTAL_COLORS[tier - 1];
    }
}
