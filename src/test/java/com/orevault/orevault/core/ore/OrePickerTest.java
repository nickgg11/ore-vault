package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ADR 0009's identity unit cases for Nick's answer to question 4 (option A), and the Epic 4 triage's ruling 5. */
class OrePickerTest {
    private static final int STEPS = 10_000;

    private static OrePicker baseStone(List<OrePicker.Candidate> ores) {
        return new OrePicker(ores, -63, 245, 1, 245, true);
    }

    /** ADR 0009 Tests: on the golden profiles a base-type rare pick at any layer from Y17 to Y245 is emerald. */
    @Test
    void aboveTheFloorSlabEveryRareVeinIsEmerald() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        for (int layer = 17; layer <= 245; layer++) {
            for (int step = 0; step < 200; step++) {
                assertEquals("minecraft:emerald_ore", picker.pick(Rarity.RARE, layer, step / 200.0), "Y=" + layer);
            }
        }
    }

    /** The mass the map puts below Y=1 is in the lowest slab, so diamond is almost all of rare there (ADR 0009: 99.6%). */
    @Test
    void theFloorSlabIsAlmostAllDiamond() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        assertTrue(share(picker, Rarity.RARE, 8, "minecraft:diamond_ore") > 0.99);
    }

    /** Above the floor slab no gold or redstone, as Nick was told (per-layer profiles keep that exact, BAL-E4-4). */
    @Test
    void goldAndRedstoneOnlyInTheFloorSlab() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        for (int layer = 17; layer <= 245; layer += 16) {
            assertEquals(0.0, share(picker, Rarity.UNCOMMON, layer, "minecraft:gold_ore"), "Y=" + layer);
            assertEquals(0.0, share(picker, Rarity.UNCOMMON, layer, "minecraft:redstone_ore"), "Y=" + layer);
        }
    }

    /** A slab where the rarity has no mass draws from the nearest slab with some. */
    @Test
    void anEmptySlabUsesTheNearestSlab() {
        OrePicker picker = new OrePicker(List.of(ore("a", Rarity.RARE, 40)), 0, 99, 0, 99, false);
        assertEquals("a", picker.pick(Rarity.RARE, 0, 0.5));
        assertEquals("a", picker.pick(Rarity.RARE, 99, 0.5));
    }

    /** Equally far both ways, the lower slab wins. */
    @Test
    void aTieGoesToTheLowerSlab() {
        OrePicker picker = new OrePicker(List.of(ore("low", Rarity.RARE, 40), ore("high", Rarity.RARE, 72)), 0, 99, 0, 99, false);
        for (int step = 0; step < 100; step++) {
            assertEquals("low", picker.pick(Rarity.RARE, 56, step / 100.0));
        }
    }

    /** Ruling 5: with no rare ore, rare veins take uncommon ores; the veins themselves are unchanged (placer test). */
    @Test
    void aMissingRarityBorrowsTheNearestOne() {
        OrePicker picker = baseStone(VanillaOres.withoutRare());
        String ore = picker.pick(Rarity.RARE, 100, 0.3);
        assertEquals(Rarity.UNCOMMON, VanillaOres.rarityOf(ore));
    }

    @Test
    void noOreAtAllIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> baseStone(List.of()));
    }

    /** RV-E4-6: the classifier may meet ores in any order; the picks do not depend on it. */
    @Test
    void theOrderOfTheCandidatesChangesNothing() {
        List<OrePicker.Candidate> shuffled = new ArrayList<>(VanillaOres.candidates());
        Collections.reverse(shuffled);
        OrePicker a = baseStone(VanillaOres.candidates());
        OrePicker b = baseStone(shuffled);
        for (Rarity rarity : Rarity.values()) {
            for (int layer = 1; layer <= 245; layer += 7) {
                for (int step = 0; step < 50; step++) {
                    assertEquals(a.pick(rarity, layer, step / 50.0), b.pick(rarity, layer, step / 50.0));
                }
            }
        }
    }

    private static double share(OrePicker picker, Rarity rarity, int layer, String ore) {
        int hits = 0;
        for (int step = 0; step < STEPS; step++) {
            hits += picker.pick(rarity, layer, (step + 0.5) / STEPS).equals(ore) ? 1 : 0;
        }
        return hits / (double) STEPS;
    }

    /** An ore whose whole mass is one layer of a 100-layer column mapped one to one. */
    private static OrePicker.Candidate ore(String id, Rarity rarity, int layer) {
        double[] masses = new double[100];
        masses[layer] = 1;
        return new OrePicker.Candidate(id, rarity, 1, new HeightProfile(0, masses));
    }
}
