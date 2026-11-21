package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.Rarity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The S12 verdicts {@code /orevault density} prints (§3.1 "How the baseline is checked", E4-S4 #254): each band within 5%
 * relative of the closed form less overlap, the rarity shares by placed blocks within 2 points, every 16-layer slab within
 * 10% of its band, the partial top slab merged into the one below.
 */
class DensityTallyTest {
    private static final OreBaseline BASE = OreBaseline.BASE;
    private static final int CHUNKS = 81;
    private static final String COAL = "minecraft:coal_ore";
    private static final String LAPIS = "minecraft:lapis_ore";
    private static final String DIAMOND = "minecraft:diamond_ore";
    private static final String EMERALD = "minecraft:emerald_ore";

    /** The figures §3.1 states for the command's expected values, so a change to the baseline or the overlap shows here. */
    @Test
    void theExpectedDensityIsTheClosedFormLessOverlap() {
        DensityTally base = new DensityTally(BASE, VaultVariant.BASE, CHUNKS);
        DensityTally expanded = new DensityTally(BASE, VaultVariant.EXPANDED, CHUNKS);
        assertEquals(BASE.density(Band.STONE, VaultVariant.BASE) * (1 - 0.017), base.expectedDensity(Band.STONE), 1e-12);
        assertEquals(BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED) * (1 - 0.033),
                expanded.expectedDensity(Band.DEEPSLATE), 1e-12);
        assertEquals(0.029928, base.expectedDensity(Band.STONE), 5e-6);
        assertEquals(0.029807, expanded.expectedDensity(Band.STONE), 5e-6);
        assertEquals(0.060109, expanded.expectedDensity(Band.DEEPSLATE), 5e-6);
    }

    /** Printed beside the expected value: the counts are raised by it and the band cut takes as much back (ADR 0009). */
    @Test
    void theEdgeFactorIsTheBandsBlockWeightedMean() {
        DensityTally expanded = new DensityTally(BASE, VaultVariant.EXPANDED, CHUNKS);
        double weighted = 0;
        for (Rarity rarity : Rarity.values()) {
            weighted += BASE.blocksPerChunk(Band.DEEPSLATE, rarity) * BASE.edgeFactor(Band.DEEPSLATE, rarity, VaultVariant.EXPANDED);
        }
        assertEquals(weighted / BASE.blocksPerChunk(Band.DEEPSLATE), expanded.edgeFactor(Band.DEEPSLATE), 1e-12);
        assertEquals(1.0229, expanded.edgeFactor(Band.DEEPSLATE), 5e-4);
        assertEquals(1.0060, expanded.edgeFactor(Band.STONE), 5e-4);
    }

    @Test
    void aBandWithinFivePercentPassesAndOneOutsideFails() {
        for (double factor : new double[] {1.045, 0.955}) {
            assertTrue(stoneAt(factor).densityPasses(Band.STONE), "at " + factor);
        }
        for (double factor : new double[] {1.055, 0.945}) {
            assertFalse(stoneAt(factor).densityPasses(Band.STONE), "at " + factor);
            assertTrue(stoneAt(factor).failures().contains("stone density"), "at " + factor);
        }
    }

    @Test
    void aShareWithinTwoPointsPassesAndOneOutsideFails() {
        double rare = BASE.share(Band.STONE, Rarity.RARE);
        assertTrue(stoneWithRareShare(rare + 0.017).sharePasses(Band.STONE, Rarity.RARE));
        assertTrue(stoneWithRareShare(rare - 0.017).sharePasses(Band.STONE, Rarity.RARE));
        assertFalse(stoneWithRareShare(rare + 0.023).sharePasses(Band.STONE, Rarity.RARE));
        assertFalse(stoneWithRareShare(rare - 0.023).sharePasses(Band.STONE, Rarity.RARE));
        assertTrue(stoneWithRareShare(rare + 0.023).failures().contains("stone rare share"));
    }

    @Test
    void sharesAreByPlacedBlocks() {
        DensityTally tally = new DensityTally(BASE, VaultVariant.BASE, 1);
        tally.add(Band.STONE, 10, Rarity.COMMON, COAL, 60);
        tally.add(Band.STONE, 10, Rarity.UNCOMMON, LAPIS, 30);
        tally.add(Band.STONE, 200, Rarity.RARE, EMERALD, 10);
        assertEquals(0.60, tally.share(Band.STONE, Rarity.COMMON), 1e-12);
        assertEquals(0.30, tally.share(Band.STONE, Rarity.UNCOMMON), 1e-12);
        assertEquals(0.10, tally.share(Band.STONE, Rarity.RARE), 1e-12);
        assertEquals(100, tally.blocks(Band.STONE));
    }

    @Test
    void aBandWithNoOreHasNoShareAndFails() {
        DensityTally tally = new DensityTally(BASE, VaultVariant.BASE, 1);
        assertEquals(0, tally.share(Band.STONE, Rarity.COMMON));
        assertFalse(tally.passes());
    }

    /** §3.1: the stone band's top slab is Y225..245 in the base type and Y224..245 expanded, deepslate's Y-31..-1. */
    @Test
    void thePartialTopSlabIsMergedIntoTheOneBelow() {
        DensityTally base = new DensityTally(BASE, VaultVariant.BASE, 1);
        DensityTally expanded = new DensityTally(BASE, VaultVariant.EXPANDED, 1);
        assertEquals(15, base.slabCount(Band.STONE));
        assertEquals(new BandBounds(1, 16), base.slabBounds(Band.STONE, 0));
        assertEquals(new BandBounds(209, 224), base.slabBounds(Band.STONE, 13));
        assertEquals(new BandBounds(225, 245), base.slabBounds(Band.STONE, 14));
        assertEquals(new BandBounds(224, 245), expanded.slabBounds(Band.STONE, 14));
        assertEquals(3, expanded.slabCount(Band.DEEPSLATE));
        assertEquals(new BandBounds(-31, -1), expanded.slabBounds(Band.DEEPSLATE, 2));

        base.add(Band.STONE, 224, Rarity.COMMON, COAL, 1);
        base.add(Band.STONE, 225, Rarity.COMMON, COAL, 10);
        base.add(Band.STONE, 245, Rarity.COMMON, COAL, 100);
        assertEquals(1, base.slabBlocks(Band.STONE, 13));
        assertEquals(110, base.slabBlocks(Band.STONE, 14));
        assertEquals(110 / (256.0 * 21), base.slabDensity(Band.STONE, 14), 1e-12);
    }

    @Test
    void aSlabWithinTenPercentOfItsBandPassesAndOneOutsideFails() {
        assertTrue(stoneWithSlab(3, 1.09).slabPasses(Band.STONE, 3));
        assertTrue(stoneWithSlab(3, 0.91).slabPasses(Band.STONE, 3));
        assertFalse(stoneWithSlab(3, 1.11).slabPasses(Band.STONE, 3));
        assertFalse(stoneWithSlab(3, 0.89).slabPasses(Band.STONE, 3));
        assertTrue(stoneWithSlab(3, 1.11).failures().contains("stone slab Y49..64"));
        assertTrue(stoneWithSlab(14, 1.13).failures().contains("stone slab Y225..245"));
    }

    /** The merged top slab is judged over its 21 layers, not 16: a uniform band passes there. */
    @Test
    void aUniformBandPassesEverything() {
        DensityTally tally = uniform(VaultVariant.EXPANDED, 1.0, Map.of());
        assertTrue(tally.passes(), String.join("\n", tally.lines()));
        assertEquals(List.of(), tally.failures());
    }

    @Test
    void identitySharesAreByPlacedBlocksWithinTheirRarity() {
        DensityTally tally = new DensityTally(BASE, VaultVariant.BASE, 1);
        tally.add(Band.STONE, 3, Rarity.RARE, DIAMOND, 9);
        tally.add(Band.STONE, 100, Rarity.RARE, EMERALD, 90);
        tally.add(Band.STONE, 200, Rarity.RARE, EMERALD, 1);
        assertEquals(Map.of(DIAMOND, 9L, EMERALD, 91L), tally.identities(Band.STONE, Rarity.RARE));
        assertTrue(tally.lines().contains("stone identity, rare: minecraft:diamond_ore 9.0%, minecraft:emerald_ore 91.0%"),
                String.join("\n", tally.lines()));
    }

    @Test
    void aLayerOutsideItsBandIsRefused() {
        DensityTally base = new DensityTally(BASE, VaultVariant.BASE, 1);
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.STONE, 0, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.STONE, 246, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.DEEPSLATE, -10, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> new DensityTally(BASE, VaultVariant.BASE, 0));
    }

    @Test
    void bandAtFollowsTheVariant() {
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.BASE, 0));
        assertEquals(Optional.of(Band.STONE), DensityTally.bandAt(VaultVariant.BASE, 1));
        assertEquals(Optional.of(Band.STONE), DensityTally.bandAt(VaultVariant.EXPANDED, 0));
        assertEquals(Optional.of(Band.DEEPSLATE), DensityTally.bandAt(VaultVariant.EXPANDED, -1));
        assertEquals(Optional.of(Band.DEEPSLATE), DensityTally.bandAt(VaultVariant.EXPANDED, -63));
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.EXPANDED, -64));
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.BASE, 246));
        assertEquals(List.of(Band.STONE), new DensityTally(BASE, VaultVariant.BASE, 1).bands());
    }

    /** The line QA quotes in the PR says what it was measured against, and the verdict names every failure. */
    @Test
    void theLinesStateTheExpectedValueAndTheVerdict() {
        DensityTally passing = uniform(VaultVariant.BASE, 1.0, Map.of());
        String text = String.join("\n", passing.lines());
        assertTrue(text.contains("expected 2.9928% = closed form 3.0446% less 1.7% overlap"), text);
        assertTrue(text.contains("edge factor 1.0060"), text);
        assertTrue(text.contains("§3.1 states 3%"), text);
        assertTrue(passing.lines().getLast().startsWith("verdict: PASS"), text);
        assertFalse(text.contains("deepslate"), text);

        DensityTally failing = uniform(VaultVariant.BASE, 1.2, Map.of());
        assertEquals("verdict: FAIL (stone density)", failing.lines().getLast());
    }

    @Test
    void countsCompareTwoTallies() {
        DensityTally one = new DensityTally(BASE, VaultVariant.EXPANDED, 1);
        DensityTally two = new DensityTally(BASE, VaultVariant.EXPANDED, 1);
        one.add(Band.DEEPSLATE, -5, Rarity.RARE, DIAMOND, 2);
        two.add(Band.DEEPSLATE, -5, Rarity.RARE, DIAMOND, 1);
        assertFalse(one.counts().equals(two.counts()));
        two.add(Band.DEEPSLATE, -6, Rarity.RARE, DIAMOND, 1);
        assertEquals(one.counts(), two.counts());
        two.add(Band.DEEPSLATE, -40, Rarity.RARE, DIAMOND, 1);
        assertFalse(one.counts().equals(two.counts()), "a block in another slab");
    }

    private static DensityTally stoneAt(double factor) {
        return uniform(VaultVariant.BASE, factor, Map.of());
    }

    private static DensityTally stoneWithSlab(int slab, double factor) {
        return uniform(VaultVariant.BASE, 1.0, Map.of(slab, factor));
    }

    /** The stone band at its expected density, every layer alike, with the rare share moved and common taking up the rest. */
    private static DensityTally stoneWithRareShare(double rareShare) {
        DensityTally tally = new DensityTally(BASE, VaultVariant.BASE, CHUNKS);
        BandBounds stone = VaultVariant.BASE.band(Band.STONE).orElseThrow();
        long perLayer = Math.round(tally.expectedDensity(Band.STONE) * 256 * CHUNKS);
        long uncommon = Math.round(perLayer * BASE.share(Band.STONE, Rarity.UNCOMMON));
        long rare = Math.round(perLayer * rareShare);
        for (int y = stone.bottomY(); y <= stone.topY(); y++) {
            tally.add(Band.STONE, y, Rarity.COMMON, COAL, perLayer - uncommon - rare);
            tally.add(Band.STONE, y, Rarity.UNCOMMON, LAPIS, uncommon);
            tally.add(Band.STONE, y, Rarity.RARE, EMERALD, rare);
        }
        return tally;
    }

    /**
     * Every band of the variant at {@code factor} times its expected density, at the closed-form shares, every layer alike,
     * except the stone slabs named, which are scaled again. Each layer's total is rounded once, so the error is
     * under half a block a layer, about 0.1% of a stone layer's ore.
     */
    private static DensityTally uniform(VaultVariant variant, double factor, Map<Integer, Double> stoneSlabs) {
        DensityTally tally = new DensityTally(BASE, variant, CHUNKS);
        for (Band band : tally.bands()) {
            BandBounds bounds = variant.band(band).orElseThrow();
            for (int y = bounds.bottomY(); y <= bounds.topY(); y++) {
                double slabFactor = 1.0;
                if (band == Band.STONE) {
                    for (Map.Entry<Integer, Double> scaled : stoneSlabs.entrySet()) {
                        BandBounds slab = tally.slabBounds(band, scaled.getKey());
                        if (y >= slab.bottomY() && y <= slab.topY()) {
                            slabFactor = scaled.getValue();
                        }
                    }
                }
                long perLayer = Math.round(tally.expectedDensity(band) * factor * slabFactor * 256 * CHUNKS);
                long uncommon = Math.round(perLayer * BASE.share(band, Rarity.UNCOMMON));
                long rare = Math.round(perLayer * BASE.share(band, Rarity.RARE));
                tally.add(band, y, Rarity.COMMON, COAL, perLayer - uncommon - rare);
                tally.add(band, y, Rarity.UNCOMMON, LAPIS, uncommon);
                tally.add(band, y, Rarity.RARE, DIAMOND, rare);
            }
        }
        return tally;
    }
}
