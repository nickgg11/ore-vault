package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The S12 verdicts {@code /orevault density} prints (§3.1 "How the baseline is checked", E4-S4 #254): each band within 5%
 * relative of the closed form less overlap, the rarity shares by placed blocks within 2 points, and every 16-layer slab
 * within 10% of its own expected density and 3 points of its expected shares, the partial top slab merged into the one
 * below. Since each ore sits at its own height (ADR 0009, amended 2026-11-21) a slab's expected figures come from the
 * profiles, so a band filled flat at its density now fails its lowest slab.
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
        DensityTally base = tally(VaultVariant.BASE, CHUNKS);
        DensityTally expanded = tally(VaultVariant.EXPANDED, CHUNKS);
        assertEquals(BASE.density(Band.STONE, VaultVariant.BASE) * (1 - 0.017), base.expectedDensity(Band.STONE), 1e-12);
        assertEquals(BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED) * (1 - 0.033),
                expanded.expectedDensity(Band.DEEPSLATE), 1e-12);
        assertEquals(0.029928, base.expectedDensity(Band.STONE), 5e-6);
        assertEquals(0.029617, expanded.expectedDensity(Band.STONE), 5e-6);
        assertEquals(0.060109, expanded.expectedDensity(Band.DEEPSLATE), 5e-6);
    }

    /**
     * §3.1's table of the base type's slabs, which the Balancer derived in {@code docs/research/vanilla-ore/heights.py}:
     * each slab's expected density, the overlap divided out, within 2% relative of the table, and its shares within the
     * table's rounding. Deepslate against §3.1's 4.7%, 3.7% and 8.3%. The tally reads the same {@link LayerSpread} the
     * placer's edge factor and the clip read, so this ties all three to the spec's figures.
     */
    @Test
    void theExpectedSlabsAreSectionThreeOnes() {
        DensityTally base = tally(VaultVariant.BASE, CHUNKS);
        double[] stated = {0.0457, 0.0295, 0.0274, 0.0284, 0.0256};
        int[][] mix = {{28, 58, 14}, {65, 33, 2}, {80, 16, 4}, {80, 16, 4}, {93, 0, 7}};
        assertEquals(stated.length, base.slabCount(Band.STONE));
        for (int slab = 0; slab < stated.length; slab++) {
            double closedForm = base.expectedSlabDensity(Band.STONE, slab) / (1 - DensityTally.STONE_OVERLAP);
            assertEquals(stated[slab], closedForm, 0.02 * stated[slab], "stone slab " + base.slabBounds(Band.STONE, slab));
            for (Rarity rarity : Rarity.values()) {
                assertEquals(mix[slab][rarity.ordinal()] / 100.0, base.expectedSlabShare(Band.STONE, slab, rarity), 0.006,
                        "stone slab " + base.slabBounds(Band.STONE, slab) + " " + rarity);
            }
        }
        DensityTally expanded = tally(VaultVariant.EXPANDED, CHUNKS);
        double[] deep = {0.047, 0.037, 0.083};
        for (int slab = 0; slab < deep.length; slab++) {
            double closedForm = expanded.expectedSlabDensity(Band.DEEPSLATE, slab) / (1 - DensityTally.DEEPSLATE_OVERLAP);
            assertEquals(deep[slab], closedForm, 0.02 * deep[slab], "deepslate slab " + expanded.slabBounds(Band.DEEPSLATE, slab));
        }
    }

    /** The slabs together hold the band: the profiles move ore between slabs, never in or out of the band. */
    @Test
    void theExpectedSlabsSumToTheBand() {
        for (VaultVariant variant : VaultVariant.values()) {
            DensityTally tally = tally(variant, CHUNKS);
            for (Band band : tally.bands()) {
                double blocks = 0;
                for (int slab = 0; slab < tally.slabCount(band); slab++) {
                    blocks += tally.expectedSlabDensity(band, slab) * 256 * tally.slabBounds(band, slab).layers();
                }
                assertEquals(tally.expectedDensity(band) * 256 * variant.band(band).orElseThrow().layers(), blocks, 1e-6,
                        variant.id() + " " + band);
            }
        }
    }

    /** A pack with no classified ore has no pickers: its slabs expect the band's density, and no edge factor. */
    @Test
    void withNoPickerASlabExpectsTheBandsDensity() {
        DensityTally tally = new DensityTally(BASE, VaultVariant.EXPANDED, Map.of(), CHUNKS);
        for (Band band : tally.bands()) {
            for (int slab = 0; slab < tally.slabCount(band); slab++) {
                assertEquals(tally.expectedDensity(band), tally.expectedSlabDensity(band, slab), 1e-12);
            }
            assertEquals(1.0, tally.edgeFactor(band), 1e-12);
        }
    }

    /** Printed beside the expected value: the counts are raised by it and the band cut takes as much back (ADR 0009). */
    @Test
    void theEdgeFactorIsTheBandsBlockWeightedMean() {
        Map<Band, OrePicker> pickers = pickers(VaultVariant.EXPANDED);
        DensityTally expanded = tally(VaultVariant.EXPANDED, CHUNKS);
        double weighted = 0;
        for (Rarity rarity : Rarity.values()) {
            weighted += BASE.blocksPerChunk(Band.DEEPSLATE, rarity)
                    * BASE.edgeFactor(Band.DEEPSLATE, rarity, pickers.get(Band.DEEPSLATE));
        }
        assertEquals(weighted / BASE.blocksPerChunk(Band.DEEPSLATE), expanded.edgeFactor(Band.DEEPSLATE), 1e-12);
        assertEquals(1.0173, expanded.edgeFactor(Band.DEEPSLATE), 5e-4);
        assertEquals(1.0048, expanded.edgeFactor(Band.STONE), 5e-4);
        assertEquals(1.0049, tally(VaultVariant.BASE, CHUNKS).edgeFactor(Band.STONE), 5e-4);
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
        assertTrue(stoneWithRareShift(-1, 0.017).sharePasses(Band.STONE, Rarity.RARE));
        assertTrue(stoneWithRareShift(-1, -0.017).sharePasses(Band.STONE, Rarity.RARE));
        assertFalse(stoneWithRareShift(-1, 0.023).sharePasses(Band.STONE, Rarity.RARE));
        assertFalse(stoneWithRareShift(-1, -0.023).sharePasses(Band.STONE, Rarity.RARE));
        assertTrue(stoneWithRareShift(-1, 0.023).failures().contains("stone rare share"));
    }

    /** ADR 0009, amended 2026-11-21: a slab's rarity shares within 3 points of its expected shares. */
    @Test
    void aSlabShareWithinThreePointsPassesAndOneOutsideFails() {
        assertTrue(stoneWithRareShift(0, 0.027).slabSharePasses(Band.STONE, 0, Rarity.RARE));
        assertTrue(stoneWithRareShift(0, -0.027).slabSharePasses(Band.STONE, 0, Rarity.RARE));
        assertFalse(stoneWithRareShift(0, 0.033).slabSharePasses(Band.STONE, 0, Rarity.RARE));
        assertFalse(stoneWithRareShift(0, -0.033).slabSharePasses(Band.STONE, 0, Rarity.RARE));
        List<String> failures = stoneWithRareShift(0, 0.033).failures();
        assertTrue(failures.contains("stone slab Y1..16 rare share"), failures.toString());
        assertTrue(failures.contains("stone slab Y1..16 common share"), failures.toString());
        assertFalse(failures.contains("stone rare share"), "one slab moves the band's share under 2 points: " + failures);
    }

    @Test
    void sharesAreByPlacedBlocks() {
        DensityTally tally = tally(VaultVariant.BASE, 1);
        tally.add(Band.STONE, 10, Rarity.COMMON, COAL, 60);
        tally.add(Band.STONE, 10, Rarity.UNCOMMON, LAPIS, 30);
        tally.add(Band.STONE, 90, Rarity.RARE, EMERALD, 10);
        assertEquals(0.60, tally.share(Band.STONE, Rarity.COMMON), 1e-12);
        assertEquals(0.30, tally.share(Band.STONE, Rarity.UNCOMMON), 1e-12);
        assertEquals(0.10, tally.share(Band.STONE, Rarity.RARE), 1e-12);
        assertEquals(100, tally.blocks(Band.STONE));
    }

    @Test
    void aBandWithNoOreHasNoShareAndFails() {
        DensityTally tally = tally(VaultVariant.BASE, 1);
        assertEquals(0, tally.share(Band.STONE, Rarity.COMMON));
        assertFalse(tally.passes());
    }

    /**
     * §3.1, at Y=100: the base type's stone band has five slabs, the top one Y65..95 with the partial slab merged; the
     * expanded type's 96 stone layers divide into six whole ones; deepslate's top is Y-31..-1.
     */
    @Test
    void thePartialTopSlabIsMergedIntoTheOneBelow() {
        DensityTally base = tally(VaultVariant.BASE, 1);
        DensityTally expanded = tally(VaultVariant.EXPANDED, 1);
        assertEquals(5, base.slabCount(Band.STONE));
        assertEquals(new BandBounds(1, 16), base.slabBounds(Band.STONE, 0));
        assertEquals(new BandBounds(49, 64), base.slabBounds(Band.STONE, 3));
        assertEquals(new BandBounds(65, 95), base.slabBounds(Band.STONE, 4));
        assertEquals(6, expanded.slabCount(Band.STONE));
        assertEquals(new BandBounds(0, 15), expanded.slabBounds(Band.STONE, 0));
        assertEquals(new BandBounds(80, 95), expanded.slabBounds(Band.STONE, 5));
        assertEquals(3, expanded.slabCount(Band.DEEPSLATE));
        assertEquals(new BandBounds(-31, -1), expanded.slabBounds(Band.DEEPSLATE, 2));

        base.add(Band.STONE, 64, Rarity.COMMON, COAL, 1);
        base.add(Band.STONE, 65, Rarity.COMMON, COAL, 10);
        base.add(Band.STONE, 95, Rarity.COMMON, COAL, 100);
        assertEquals(1, base.slabBlocks(Band.STONE, 3));
        assertEquals(110, base.slabBlocks(Band.STONE, 4));
        assertEquals(110 / (256.0 * 31), base.slabDensity(Band.STONE, 4), 1e-12);
    }

    @Test
    void aSlabWithinTenPercentOfItsExpectedDensityPassesAndOneOutsideFails() {
        assertTrue(stoneWithSlab(3, 1.09).slabPasses(Band.STONE, 3));
        assertTrue(stoneWithSlab(3, 0.91).slabPasses(Band.STONE, 3));
        assertFalse(stoneWithSlab(3, 1.11).slabPasses(Band.STONE, 3));
        assertFalse(stoneWithSlab(3, 0.89).slabPasses(Band.STONE, 3));
        assertTrue(stoneWithSlab(3, 1.11).failures().contains("stone slab Y49..64"));
        assertTrue(stoneWithSlab(4, 1.13).failures().contains("stone slab Y65..95"));
    }

    /** Every band at its expected figures, slab by slab, passes everything, the merged top slab judged over its 31 layers. */
    @Test
    void aBandAtItsExpectedSlabsPassesEverything() {
        for (VaultVariant variant : VaultVariant.values()) {
            DensityTally tally = expectedFill(variant, 1.0, Map.of(), -1, 0);
            assertTrue(tally.passes(), String.join("\n", tally.lines()));
            assertEquals(List.of(), tally.failures());
        }
    }

    /**
     * The guard on the per-slab expectation: the stone band filled flat at its own density, which passed every slab before
     * ore followed height, fails Y1..16, where the profiles put about 4.5% (ADR 0009, amended 2026-11-21).
     */
    @Test
    void aFlatBandFailsItsLowestSlab() {
        DensityTally tally = tally(VaultVariant.BASE, CHUNKS);
        BandBounds stone = VaultVariant.BASE.band(Band.STONE).orElseThrow();
        long perLayer = Math.round(tally.expectedDensity(Band.STONE) * 256 * CHUNKS);
        long uncommon = Math.round(perLayer * BASE.share(Band.STONE, Rarity.UNCOMMON));
        long rare = Math.round(perLayer * BASE.share(Band.STONE, Rarity.RARE));
        for (int y = stone.bottomY(); y <= stone.topY(); y++) {
            tally.add(Band.STONE, y, Rarity.COMMON, COAL, perLayer - uncommon - rare);
            tally.add(Band.STONE, y, Rarity.UNCOMMON, LAPIS, uncommon);
            tally.add(Band.STONE, y, Rarity.RARE, EMERALD, rare);
        }
        assertTrue(tally.densityPasses(Band.STONE));
        assertFalse(tally.slabPasses(Band.STONE, 0));
        assertTrue(tally.failures().contains("stone slab Y1..16"), tally.failures().toString());
    }

    @Test
    void identitySharesAreByPlacedBlocksWithinTheirRarity() {
        DensityTally tally = tally(VaultVariant.BASE, 1);
        tally.add(Band.STONE, 3, Rarity.RARE, DIAMOND, 9);
        tally.add(Band.STONE, 50, Rarity.RARE, EMERALD, 90);
        tally.add(Band.STONE, 90, Rarity.RARE, EMERALD, 1);
        assertEquals(Map.of(DIAMOND, 9L, EMERALD, 91L), tally.identities(Band.STONE, Rarity.RARE));
        assertTrue(tally.lines().contains("stone identity, rare: minecraft:diamond_ore 9.0%, minecraft:emerald_ore 91.0%"),
                String.join("\n", tally.lines()));
    }

    @Test
    void aLayerOutsideItsBandIsRefused() {
        DensityTally base = tally(VaultVariant.BASE, 1);
        DensityTally expanded = tally(VaultVariant.EXPANDED, 1);
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.STONE, 0, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.STONE, 96, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> expanded.add(Band.STONE, 96, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> base.add(Band.DEEPSLATE, -10, Rarity.COMMON, COAL, 1));
        assertThrows(IllegalArgumentException.class, () -> new DensityTally(BASE, VaultVariant.BASE, pickers(VaultVariant.BASE), 0));
    }

    @Test
    void bandAtFollowsTheVariant() {
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.BASE, 0));
        assertEquals(Optional.of(Band.STONE), DensityTally.bandAt(VaultVariant.BASE, 1));
        assertEquals(Optional.of(Band.STONE), DensityTally.bandAt(VaultVariant.BASE, 95));
        assertEquals(Optional.of(Band.STONE), DensityTally.bandAt(VaultVariant.EXPANDED, 0));
        assertEquals(Optional.of(Band.DEEPSLATE), DensityTally.bandAt(VaultVariant.EXPANDED, -1));
        assertEquals(Optional.of(Band.DEEPSLATE), DensityTally.bandAt(VaultVariant.EXPANDED, -63));
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.EXPANDED, -64));
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.BASE, 96));
        assertEquals(Optional.empty(), DensityTally.bandAt(VaultVariant.EXPANDED, 96));
        assertEquals(List.of(Band.STONE), tally(VaultVariant.BASE, 1).bands());
    }

    /** The line QA quotes in the PR says what it was measured against, and the verdict names every failure. */
    @Test
    void theLinesStateTheExpectedValueAndTheVerdict() {
        DensityTally passing = stoneAt(1.0);
        String text = String.join("\n", passing.lines());
        assertTrue(text.contains("expected 2.9928% = closed form 3.0446% less 1.7% overlap"), text);
        assertTrue(text.contains("edge factor 1.0049"), text);
        assertTrue(text.contains("§3.1 states 3%"), text);
        assertTrue(text.contains("every slab within 10% of its expected density and 3 points of its expected shares"), text);
        assertTrue(text.contains("stone slab Y1..16: "), text);
        assertTrue(text.contains("; expected 4.4966%;"), text);
        assertTrue(passing.lines().getLast().startsWith("verdict: PASS"), text);
        assertFalse(text.contains("deepslate"), text);

        DensityTally failing = stoneAt(1.06);
        assertEquals("verdict: FAIL (stone density)", failing.lines().getLast());
    }

    @Test
    void countsCompareTwoTallies() {
        DensityTally one = tally(VaultVariant.EXPANDED, 1);
        DensityTally two = tally(VaultVariant.EXPANDED, 1);
        one.add(Band.DEEPSLATE, -5, Rarity.RARE, DIAMOND, 2);
        two.add(Band.DEEPSLATE, -5, Rarity.RARE, DIAMOND, 1);
        assertFalse(one.counts().equals(two.counts()));
        two.add(Band.DEEPSLATE, -6, Rarity.RARE, DIAMOND, 1);
        assertEquals(one.counts(), two.counts());
        two.add(Band.DEEPSLATE, -40, Rarity.RARE, DIAMOND, 1);
        assertFalse(one.counts().equals(two.counts()), "a block in another slab");
    }

    private static Map<Band, OrePicker> pickers(VaultVariant variant) {
        return OrePickers.forVariant(variant, VanillaOres.candidates());
    }

    private static DensityTally tally(VaultVariant variant, int chunks) {
        return new DensityTally(BASE, variant, pickers(variant), chunks);
    }

    private static DensityTally stoneAt(double factor) {
        return expectedFill(VaultVariant.BASE, factor, Map.of(), -1, 0);
    }

    private static DensityTally stoneWithSlab(int slab, double factor) {
        return expectedFill(VaultVariant.BASE, 1.0, Map.of(slab, factor), -1, 0);
    }

    /** The base stone band at its expected slabs, the rare share moved by {@code shift} in one slab, or all for -1. */
    private static DensityTally stoneWithRareShift(int slab, double shift) {
        return expectedFill(VaultVariant.BASE, 1.0, Map.of(), slab, shift);
    }

    /**
     * Every band of the variant at {@code factor} times each slab's expected density and at its expected shares, except
     * the stone slabs named, which are scaled again, and the rare share of stone slab {@code shiftSlab} (every stone slab
     * for -1) moved by {@code rareShift}, common taking up the difference. Each slab's ore goes on its lowest layer, since
     * the tally counts by slab. Each slab's total is rounded once, so the error is under a block in thousands.
     */
    private static DensityTally expectedFill(VaultVariant variant, double factor, Map<Integer, Double> stoneSlabs,
            int shiftSlab, double rareShift) {
        DensityTally tally = tally(variant, CHUNKS);
        for (Band band : tally.bands()) {
            for (int slab = 0; slab < tally.slabCount(band); slab++) {
                BandBounds bounds = tally.slabBounds(band, slab);
                double slabFactor = band == Band.STONE ? stoneSlabs.getOrDefault(slab, 1.0) : 1.0;
                double shift = band == Band.STONE && (shiftSlab == -1 || shiftSlab == slab) ? rareShift : 0;
                long total = Math.round(tally.expectedSlabDensity(band, slab) * factor * slabFactor * 256 * CHUNKS * bounds.layers());
                long uncommon = Math.round(total * tally.expectedSlabShare(band, slab, Rarity.UNCOMMON));
                long rare = Math.round(total * (tally.expectedSlabShare(band, slab, Rarity.RARE) + shift));
                tally.add(band, bounds.bottomY(), Rarity.COMMON, COAL, total - uncommon - rare);
                tally.add(band, bounds.bottomY(), Rarity.UNCOMMON, LAPIS, uncommon);
                tally.add(band, bounds.bottomY(), Rarity.RARE, DIAMOND, rare);
            }
        }
        return tally;
    }
}
