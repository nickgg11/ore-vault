package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** §10 {@code ore_classification.overrides}: each entry validated, a malformed one rejected with a reason, never thrown. */
class OverridesTest {
    private static final Set<String> TAG = Set.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:coal_ore", "examplemod:tin_ore");

    @Test
    void aWellFormedEntryApplies() {
        Overrides overrides = Overrides.parse(List.of("minecraft:diamond_ore=rare", " examplemod:tin_ore = Common "), TAG);
        assertEquals(Map.of("minecraft:diamond_ore", Rarity.RARE, "examplemod:tin_ore", Rarity.COMMON), overrides.byBlock());
        assertTrue(overrides.rejected().isEmpty(), overrides.rejected().toString());
        assertEquals(Rarity.RARE, overrides.of("minecraft:diamond_ore"));
        assertNull(overrides.of("minecraft:coal_ore"));
    }

    /** Epic 4 triage (DEV-E4-4, RV-E4-4): {@code minecraft:stone=common} must not make stone pay. */
    @Test
    void aBlockOutsideTheOreTagIsRejected() {
        Overrides overrides = Overrides.parse(List.of("minecraft:stone=common"), TAG);
        assertTrue(overrides.byBlock().isEmpty());
        assertEquals(1, overrides.rejected().size());
        assertTrue(overrides.rejected().get(0).contains("#c:ores"), overrides.rejected().get(0));
    }

    @Test
    void malformedEntriesAreRejectedOneByOne() {
        List<String> bad = List.of("", "minecraft:coal_ore", "minecraft:coal_ore=", "=rare", "coal_ore=rare",
                "minecraft:coal_ore=epic", "minecraft:coal_ore=rare=common", "Minecraft:Coal_Ore=rare");
        Overrides overrides = Overrides.parse(bad, TAG);
        assertTrue(overrides.byBlock().isEmpty(), overrides.byBlock().toString());
        assertEquals(bad.size(), overrides.rejected().size(), overrides.rejected().toString());
    }

    /** One bad entry never takes the good ones with it, which NeoForge's list validation would do. */
    @Test
    void aBadEntryLeavesTheOthers() {
        Overrides overrides = Overrides.parse(List.of("minecraft:coal_ore=epic", "minecraft:coal_ore=uncommon"), TAG);
        assertEquals(Map.of("minecraft:coal_ore", Rarity.UNCOMMON), overrides.byBlock());
        assertEquals(1, overrides.rejected().size());
    }

    @Test
    void aBlockNamedTwiceAppliesOnceOrNotAtAll() {
        Overrides same = Overrides.parse(List.of("minecraft:coal_ore=rare", "minecraft:coal_ore=rare"), TAG);
        assertEquals(Map.of("minecraft:coal_ore", Rarity.RARE), same.byBlock());
        assertTrue(same.rejected().isEmpty());

        Overrides clash = Overrides.parse(List.of("minecraft:coal_ore=rare", "minecraft:coal_ore=common"), TAG);
        assertTrue(clash.byBlock().isEmpty(), clash.byBlock().toString());
        assertEquals(1, clash.rejected().size());
    }

    @Test
    void noneIsEmpty() {
        assertTrue(Overrides.NONE.byBlock().isEmpty());
        assertTrue(Overrides.NONE.rejected().isEmpty());
    }
}
