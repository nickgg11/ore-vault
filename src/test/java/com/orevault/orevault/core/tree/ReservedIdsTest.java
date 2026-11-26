package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** §6.1 Reserved node ids: exactly the seven, and the set cannot be changed at runtime. */
class ReservedIdsTest {
    @Test
    void exactlyTheSevenOfSection61() {
        assertEquals(Set.of("common_ore_boost", "uncommon_ore_boost", "rare_ore_boost", "ore_sense", "motherlode",
                "efficient_miner", "disturbed_zone_unlock"), ReservedIds.ALL);
    }

    @Test
    void theSetIsUnmodifiable() {
        assertThrows(UnsupportedOperationException.class, () -> ReservedIds.ALL.add("vein_expansion"));
    }

    @Test
    void isReservedMatchesExactly() {
        assertTrue(ReservedIds.isReserved("motherlode"));
        assertFalse(ReservedIds.isReserved("Motherlode"));
        assertFalse(ReservedIds.isReserved("vein_singularity"));
    }
}
