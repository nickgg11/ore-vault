package com.orevault.orevault.core.tree;

import java.util.Set;

/**
 * The ids §6.1 "Reserved node ids" retires for good, so a stray old save or a leftover lang key can never be read as a
 * different node. No {@link SkillId} or {@link ForkOption} may use one (unit tests), and {@link TreeIds} reports any in
 * the layout. Never writing one to a save is E6-S3's (#251). An id retired later joins this set in its own PR
 * ({@code docs/tree-layout-format.md}, "Frozen ids and free fields").
 */
public final class ReservedIds {
    /** The seven ids of §6.1, immutable. */
    public static final Set<String> ALL = Set.of("common_ore_boost", "uncommon_ore_boost", "rare_ore_boost",
            "ore_sense", "motherlode", "efficient_miner", "disturbed_zone_unlock");

    private ReservedIds() {
    }

    /** Whether {@code id} is reserved, compared exactly. */
    public static boolean isReserved(String id) {
        return ALL.contains(id);
    }
}
