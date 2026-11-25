package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * Each ore picks its own height (§3.1 "Where each ore sits"; ADR 0009, amended 2026-11-21, which replaces question 4's
 * identity cases), the region rule (Manager, 2026-11-21) and the Epic 4 triage's ruling 5.
 */
class OrePickerTest {
    private static final int DRAWS = 10_000;

    /** The base type's stone band, Y1..95, as {@code VaultVariant} gives it. */
    private static OrePicker baseStone(List<OrePicker.Candidate> ores) {
        return new OrePicker(ores, 1, 95);
    }

    /**
     * ADR 0009's new unit case: on the golden fixture, base type, the pick shares by ore within 0.5 points of §3.1's
     * (heights.txt, B100 base): common 53.1 coal, 30.2 copper, 16.7 iron; uncommon 58.8 lapis, 24.2 gold, 17.0 redstone;
     * rare 48.0 diamond, 52.0 emerald. Checked on the table's shares and on the frequencies of 10,000 draws.
     */
    @Test
    void thePickSharesAreSectionThreeOnesWithinHalfAPoint() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        Map<String, Double> expected = Map.of(
                "minecraft:coal_ore", 0.531, "minecraft:copper_ore", 0.302, "minecraft:iron_ore", 0.167,
                "minecraft:lapis_ore", 0.588, "minecraft:gold_ore", 0.242, "minecraft:redstone_ore", 0.170,
                "minecraft:diamond_ore", 0.480, "minecraft:emerald_ore", 0.520);
        int seen = 0;
        for (Rarity rarity : Rarity.values()) {
            OrePicker.Table table = picker.table(rarity);
            int[] hits = new int[table.size()];
            for (int i = 0; i < DRAWS; i++) {
                hits[table.pickOre((i + 0.5) / DRAWS)]++;
            }
            for (int ore = 0; ore < table.size(); ore++) {
                double want = expected.get(table.id(ore));
                assertEquals(want, table.share(ore), 0.005, table.id(ore));
                assertEquals(want, hits[ore] / (double) DRAWS, 0.005, table.id(ore) + " drawn");
                assertEquals(VanillaOres.rarityOf(table.id(ore)), rarity);
                seen++;
            }
        }
        assertEquals(8, seen);
    }

    /**
     * ADR 0009's new unit case: the layer draw's histogram over 10,000 draws matches the profile inside the band. Spread
     * evenly over [0, 1), each layer's count is within one draw of 10,000 times its share of the ore's in-band mass, which
     * is exact inverse-cumulative sampling; for every ore of every rarity, in both of the expanded type's bands.
     */
    @Test
    void theLayerDrawMatchesTheProfile() {
        for (int[] band : new int[][] {{1, 95}, {0, 95}, {-63, -1}}) {
            OrePicker picker = new OrePicker(VanillaOres.candidates(), band[0], band[1]);
            for (Rarity rarity : Rarity.values()) {
                OrePicker.Table table = picker.table(rarity);
                for (int ore = 0; ore < table.size(); ore++) {
                    double[] mass = profile(table.id(ore)).layers(band[0], band[1]);
                    double total = sum(mass);
                    int[] hits = new int[mass.length];
                    for (int i = 0; i < DRAWS; i++) {
                        hits[table.pickMiddle(ore, (i + 0.5) / DRAWS) - band[0]]++;
                    }
                    for (int layer = 0; layer < mass.length; layer++) {
                        assertEquals(DRAWS * mass[layer] / total, hits[layer], 1.0, table.id(ore) + " Y=" + (band[0] + layer));
                    }
                }
            }
        }
    }

    /** The same histogram from 10,000 random draws: diamond's Y1..16 within a total variation of 0.05 (about 3x its noise). */
    @Test
    void randomLayerDrawsFollowTheProfile() {
        OrePicker.Table rare = baseStone(VanillaOres.candidates()).table(Rarity.RARE);
        int diamond = index(rare, "minecraft:diamond_ore");
        double[] mass = profile("minecraft:diamond_ore").layers(1, 95);
        double total = sum(mass);
        int[] hits = new int[mass.length];
        SplittableRandom random = new SplittableRandom(0x5EED);
        for (int i = 0; i < DRAWS; i++) {
            hits[rare.pickMiddle(diamond, random.nextDouble()) - 1]++;
        }
        double variation = 0;
        for (int layer = 0; layer < mass.length; layer++) {
            variation += Math.abs(hits[layer] / (double) DRAWS - mass[layer] / total) / 2;
        }
        assertTrue(variation < 0.05, "total variation " + variation);
    }

    /** ADR 0009's new unit case, at the picker: a middle is never drawn at a layer where the ore's profile has no mass. */
    @Test
    void noMiddleLandsWhereTheProfileHasNoMass() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        for (Rarity rarity : Rarity.values()) {
            OrePicker.Table table = picker.table(rarity);
            for (int ore = 0; ore < table.size(); ore++) {
                HeightProfile profile = profile(table.id(ore));
                for (int i = 0; i < DRAWS; i++) {
                    int y = table.pickMiddle(ore, i / (double) DRAWS);
                    assertTrue(profile.massAt(y) > 0, table.id(ore) + " drawn at Y=" + y);
                }
                assertTrue(profile.massAt(table.pickMiddle(ore, Math.nextDown(1.0))) > 0, table.id(ore) + " at the top draw");
            }
        }
    }

    /** §3.1: an ore with no mass in a band is never picked there; vanilla's deepslate band has no coal (it starts at Y=0). */
    @Test
    void anOreWithNoMassInTheBandIsNeverPicked() {
        OrePicker deepslate = new OrePicker(VanillaOres.candidates(), -63, -1);
        OrePicker.Table common = deepslate.table(Rarity.COMMON);
        for (int ore = 0; ore < common.size(); ore++) {
            assertTrue(!common.id(ore).equals("minecraft:coal_ore"), "coal is in the deepslate table");
        }
        assertEquals(2, common.size());
    }

    /** The middle distribution sums to 1 and is the profile cut to the band and renormalised. */
    @Test
    void theMiddleIsTheProfileRenormalisedInTheBand() {
        OrePicker.Table common = baseStone(VanillaOres.candidates()).table(Rarity.COMMON);
        for (int ore = 0; ore < common.size(); ore++) {
            double[] middle = common.middle(ore);
            double[] mass = profile(common.id(ore)).layers(1, 95);
            assertEquals(1.0, sum(middle), 1e-12);
            for (int layer = 0; layer < middle.length; layer++) {
                assertEquals(mass[layer] / sum(mass), middle[layer], 1e-12);
            }
        }
    }

    /** Where no ore of a rarity has mass in the band, the ore is by weight alone and the middle evenly through it. */
    @Test
    void aRarityWithNoMassInTheBandDrawsByWeightAndEvenly() {
        OrePicker picker = new OrePicker(List.of(ore("high", Rarity.RARE, 3, 250), ore("higher", Rarity.RARE, 1, 280)), 0, 99);
        OrePicker.Table rare = picker.table(Rarity.RARE);
        assertEquals(2, rare.size());
        assertEquals(0.75, rare.share(index(rare, "high")), 1e-12);
        double[] even = rare.middle(index(rare, "higher"));
        for (double layer : even) {
            assertEquals(0.01, layer, 1e-12);
        }
        assertEquals(0, rare.pickMiddle(0, 0.0));
        assertEquals(99, rare.pickMiddle(0, Math.nextDown(1.0)));
    }

    /**
     * The region rule (Manager, 2026-11-21): a request over part of the band draws its ore by weight times mass inside the
     * region and its middle from the profile cut to the region. In Deep Veins' lowest 30 layers of the base stone band,
     * Y1..30, diamond (which ends at Y=16) is a larger share of rare than in the whole band, and its middles stay in Y1..16.
     */
    @Test
    void aRegionDrawsFromTheProfileInsideIt() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        OrePicker.Table region = picker.table(Rarity.RARE, 1, 30);
        assertEquals(1, region.regionBottomY());
        assertEquals(30, region.regionTopY());
        int diamond = index(region, "minecraft:diamond_ore");
        double[] diamondMass = profile("minecraft:diamond_ore").layers(1, 30);
        double[] emeraldMass = profile("minecraft:emerald_ore").layers(1, 30);
        double weightDiamond = weight("minecraft:diamond_ore") * sum(diamondMass);
        double weightEmerald = weight("minecraft:emerald_ore") * sum(emeraldMass);
        assertEquals(weightDiamond / (weightDiamond + weightEmerald), region.share(diamond), 1e-12);
        assertTrue(region.share(diamond) > picker.table(Rarity.RARE).share(index(picker.table(Rarity.RARE), "minecraft:diamond_ore")),
                "diamond is a larger share of the low region than of the band");
        for (int i = 0; i < DRAWS; i++) {
            int y = region.pickMiddle(diamond, (i + 0.5) / DRAWS);
            assertTrue(y >= 1 && y <= 16, "diamond's middle at Y=" + y);
        }
        assertSame(picker.table(Rarity.RARE), picker.table(Rarity.RARE, 1, 95), "the whole band is the band's own table");
    }

    /** In a region where none of the rarity's ores has mass, the ore is by weight and the middle even in the region. */
    @Test
    void aRegionWithNoMassDrawsByWeightAndEvenlyThere() {
        // Gold ends at Y=32, redstone at Y=15 and lapis at Y=64, so no uncommon ore has mass in Y90..95.
        OrePicker.Table uncommon = baseStone(VanillaOres.candidates()).table(Rarity.UNCOMMON, 90, 95);
        assertEquals(3, uncommon.size());
        double total = weight("minecraft:gold_ore") + weight("minecraft:lapis_ore") + weight("minecraft:redstone_ore");
        assertEquals(weight("minecraft:lapis_ore") / total, uncommon.share(index(uncommon, "minecraft:lapis_ore")), 1e-12);
        for (int ore = 0; ore < uncommon.size(); ore++) {
            for (double layer : uncommon.middle(ore)) {
                assertEquals(1 / 6.0, layer, 1e-12);
            }
        }
        OrePicker.Table rare = baseStone(VanillaOres.candidates()).table(Rarity.RARE, 90, 95);
        assertEquals(1, rare.size(), "emerald's triangle runs to Y=480, so it alone has rare mass in Y90..95");
    }

    @Test
    void aRegionOutsideTheBandIsRefused() {
        OrePicker picker = baseStone(VanillaOres.candidates());
        assertThrows(IllegalArgumentException.class, () -> picker.table(Rarity.RARE, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> picker.table(Rarity.RARE, 90, 96));
    }

    /** Ruling 5: with no rare ore, rare veins take uncommon ores; the veins themselves are unchanged (placer test). */
    @Test
    void aMissingRarityBorrowsTheNearestOne() {
        OrePicker.Table rare = baseStone(VanillaOres.withoutRare()).table(Rarity.RARE);
        for (int ore = 0; ore < rare.size(); ore++) {
            assertEquals(Rarity.UNCOMMON, VanillaOres.rarityOf(rare.id(ore)));
        }
        assertEquals(Rarity.UNCOMMON, VanillaOres.rarityOf(rare.id(rare.pickOre(0.3))));
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
            for (int step = 0; step < 200; step++) {
                double draw = step / 200.0;
                int oreA = a.table(rarity).pickOre(draw);
                int oreB = b.table(rarity).pickOre(draw);
                assertEquals(a.table(rarity).id(oreA), b.table(rarity).id(oreB));
                assertEquals(a.table(rarity).pickMiddle(oreA, draw), b.table(rarity).pickMiddle(oreB, draw));
            }
        }
    }

    private static int index(OrePicker.Table table, String id) {
        for (int ore = 0; ore < table.size(); ore++) {
            if (table.id(ore).equals(id)) {
                return ore;
            }
        }
        throw new AssertionError(id + " is not in the table");
    }

    private static HeightProfile profile(String id) {
        return VanillaOres.candidates().stream().filter(c -> c.groupId().equals(id)).findFirst().orElseThrow().profile();
    }

    private static double weight(String id) {
        return VanillaOres.candidates().stream().filter(c -> c.groupId().equals(id)).findFirst().orElseThrow().weight();
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }

    /** An ore whose whole mass is one layer of a 300-layer column from Y=0. */
    private static OrePicker.Candidate ore(String id, Rarity rarity, double weight, int layer) {
        double[] masses = new double[300];
        masses[layer] = 1;
        return new OrePicker.Candidate(id, rarity, weight, new HeightProfile(0, masses));
    }
}
