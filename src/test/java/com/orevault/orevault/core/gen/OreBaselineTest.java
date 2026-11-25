package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.HeightProfile;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * §3.1's baseline in closed form (ADR 0009). The placed-block check over 81 chunks comes with the placer (E4-S1b); this
 * pins the derived figures the curve will read. S12's tolerances: each band's density within 5% relative, shares within
 * 2 points.
 */
class OreBaselineTest {
    private static final OreBaseline BASE = OreBaseline.BASE;

    @Test
    void theStoneBandIsThreePercent() {
        assertRelative(0.03, BASE.density(Band.STONE, VaultVariant.BASE), 0.05);
    }

    @Test
    void theDeepslateBandIsSixPercent() {
        assertRelative(0.06, BASE.density(Band.DEEPSLATE, VaultVariant.EXPANDED), 0.05);
    }

    /** BAL-E4-6, at Y=100: the same counts over 96 layers, 1.0% thinner, and compared against that (§3.1). */
    @Test
    void theExpandedStoneBandIsOneLayerThinner() {
        double ratio = BASE.density(Band.STONE, VaultVariant.EXPANDED) / BASE.density(Band.STONE, VaultVariant.BASE);
        assertEquals(95.0 / 96.0, ratio, 1e-12);
    }

    /**
     * ADR 0009, amended 2026-11-21: the stone counts follow the band's height, the 245-layer band's 141, 82 and 33 times
     * 95/245, so the density §4.3 prices is exactly what it was over 245 layers (3.04%).
     */
    @Test
    void theStoneCountsFollowTheBandsHeight() {
        double[] old = {141, 82, 33};
        for (Rarity rarity : Rarity.values()) {
            assertEquals(old[rarity.ordinal()] * 95 / 245, BASE.veins(Band.STONE, rarity).perChunk(), 1e-12, rarity.toString());
        }
        assertEquals(54.67, BASE.veins(Band.STONE, Rarity.COMMON).perChunk(), 0.005);
        assertEquals(31.80, BASE.veins(Band.STONE, Rarity.UNCOMMON).perChunk(), 0.005);
        assertEquals(12.80, BASE.veins(Band.STONE, Rarity.RARE).perChunk(), 0.005);
        double oldDensity = 0;
        for (Rarity rarity : Rarity.values()) {
            oldDensity += old[rarity.ordinal()] * BASE.veins(Band.STONE, rarity).meanBlocks();
        }
        assertEquals(oldDensity / (256.0 * 245), BASE.density(Band.STONE, VaultVariant.BASE), 1e-12);
        assertEquals(0.03045, BASE.density(Band.STONE, VaultVariant.BASE), 0.00005);
        assertEquals(9 * 95 / 245.0, BASE.gravel().get(Band.STONE).perChunk(), 1e-12);
        assertEquals(3 * 95 / 245.0, BASE.clay().get(Band.STONE).perChunk(), 1e-12);
    }

    @Test
    void theStoneMixIs68_25_7() {
        assertPoints(0.68, BASE.share(Band.STONE, Rarity.COMMON));
        assertPoints(0.25, BASE.share(Band.STONE, Rarity.UNCOMMON));
        assertPoints(0.07, BASE.share(Band.STONE, Rarity.RARE));
    }

    @Test
    void theDeepslateMixIs60_30_10() {
        assertPoints(0.60, BASE.share(Band.DEEPSLATE, Rarity.COMMON));
        assertPoints(0.30, BASE.share(Band.DEEPSLATE, Rarity.UNCOMMON));
        assertPoints(0.10, BASE.share(Band.DEEPSLATE, Rarity.RARE));
    }

    /** §3.1: common 9.2 blocks a vein, uncommon 5.8, rare 4.05. */
    @Test
    void meanBlocksPerVeinMatchTheSpec() {
        assertRelative(9.2, BASE.veins(Band.STONE, Rarity.COMMON).meanBlocks(), 0.02);
        assertRelative(5.8, BASE.veins(Band.STONE, Rarity.UNCOMMON).meanBlocks(), 0.02);
        assertRelative(4.05, BASE.veins(Band.STONE, Rarity.RARE).meanBlocks(), 0.02);
    }

    /** BAL-E4-5: about +0.4 to +0.65% in stone and +1.6 to +2.5% in deepslate, common highest. */
    /**
     * ADR 0009, amended 2026-11-21: the edge factor is one over the blocks a rarity keeps in the band once each ore sits at
     * its own height. The figures are {@code docs/research/vanilla-ore/heights.txt}'s B100 rows, from the same profiles and
     * kernel: under 1% in stone, where only coal, diamond and emerald reach the band's edges, and highest for common in
     * deepslate, whose iron peaks at the band's top.
     */
    @Test
    void theEdgeFactorsAreTheHeightsModelsOnes() {
        Map<Band, OrePicker> base = OrePickers.forVariant(VaultVariant.BASE, VanillaOres.candidates());
        Map<Band, OrePicker> expanded = OrePickers.forVariant(VaultVariant.EXPANDED, VanillaOres.candidates());
        assertEdge(1.0055, BASE.edgeFactor(Band.STONE, Rarity.COMMON, base.get(Band.STONE)));
        assertEdge(1.0028, BASE.edgeFactor(Band.STONE, Rarity.UNCOMMON, base.get(Band.STONE)));
        assertEdge(1.0065, BASE.edgeFactor(Band.STONE, Rarity.RARE, base.get(Band.STONE)));
        assertEdge(1.0054, BASE.edgeFactor(Band.STONE, Rarity.COMMON, expanded.get(Band.STONE)));
        assertEdge(1.0027, BASE.edgeFactor(Band.STONE, Rarity.UNCOMMON, expanded.get(Band.STONE)));
        assertEdge(1.0063, BASE.edgeFactor(Band.STONE, Rarity.RARE, expanded.get(Band.STONE)));
        assertEdge(1.0250, BASE.edgeFactor(Band.DEEPSLATE, Rarity.COMMON, expanded.get(Band.DEEPSLATE)));
        assertEdge(1.0073, BASE.edgeFactor(Band.DEEPSLATE, Rarity.UNCOMMON, expanded.get(Band.DEEPSLATE)));
        assertEdge(1.0025, BASE.edgeFactor(Band.DEEPSLATE, Rarity.RARE, expanded.get(Band.DEEPSLATE)));
        for (Rarity rarity : Rarity.values()) {
            double stone = BASE.edgeFactor(Band.STONE, rarity, base.get(Band.STONE)) - 1;
            double deepslate = BASE.edgeFactor(Band.DEEPSLATE, rarity, expanded.get(Band.DEEPSLATE)) - 1;
            assertTrue(stone > 0 && stone < 0.01, rarity + " stone " + stone);
            assertTrue(deepslate > 0 && deepslate < 0.0265, rarity + " deepslate " + deepslate);
            assertTrue(deepslate <= BASE.edgeFactor(Band.DEEPSLATE, Rarity.COMMON, expanded.get(Band.DEEPSLATE)) - 1);
        }
    }

    /** A picker whose ores sit in the middle of the band, far from both edges, needs no factor. */
    @Test
    void oresFarFromTheEdgesNeedNoFactor() {
        double[] mass = new double[VanillaOres.LAYERS];
        for (int y = 40; y <= 50; y++) {
            mass[y - VanillaOres.FLOOR_Y] = 1;
        }
        HeightProfile middle = new HeightProfile(VanillaOres.FLOOR_Y, mass);
        OrePicker picker = new OrePicker(List.of(new OrePicker.Candidate("test:ore", Rarity.COMMON, 1, middle)), 1, 95);
        assertEquals(1.0, BASE.edgeFactor(Band.STONE, Rarity.COMMON, picker), 1e-12);
    }

    private static void assertEdge(double expected, double actual) {
        assertEquals(expected, actual, 0.0003, "edge factor");
    }

    /** Rule 8 and ADR 0009: the third copy of the band heights agrees with the variant. */
    @Test
    void theBandHeightsAgreeWithTheVariants() {
        assertEquals(OreBaseline.STONE_BAND_LAYERS, VaultVariant.BASE.band(Band.STONE).orElseThrow().layers());
        assertEquals(OreBaseline.DEEPSLATE_BAND_LAYERS, VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow().layers());
        assertEquals(OreBaseline.STONE_BAND_LAYERS + 1, VaultVariant.EXPANDED.band(Band.STONE).orElseThrow().layers());
    }

    @Test
    void theBaseTypeHasNoDeepslateDensity() {
        assertThrows(IllegalArgumentException.class, () -> BASE.density(Band.DEEPSLATE, VaultVariant.BASE));
    }

    /** §3.1: gravel about 1.6% and clay about 0.5% of the stone band. */
    @Test
    void gravelAndClayMatchTheSpec() {
        double stoneVolume = 256.0 * OreBaseline.STONE_BAND_LAYERS;
        double blob = VeinShape.meanBlocks(BASE.gravel().get(Band.STONE).size());
        assertRelative(0.016, BASE.gravel().get(Band.STONE).perChunk() * blob / stoneVolume, 0.05);
        assertRelative(0.005, BASE.clay().get(Band.STONE).perChunk() * blob / stoneVolume, 0.10);
    }

    private static void assertRelative(double expected, double actual, double relative) {
        assertTrue(Math.abs(actual - expected) <= expected * relative,
                "expected " + expected + " within " + (relative * 100) + "%, got " + actual);
    }

    private static void assertPoints(double expected, double actual) {
        assertTrue(Math.abs(actual - expected) <= 0.02, "expected " + expected + " within 2 points, got " + actual);
    }

    /** PR419-RV-1: iteration follows the enums' order, so a chunk seed places the same veins after a restart (ADR 0003). */
    @Test
    void theMapsIterateInEnumOrder() {
        assertEquals(List.of(Band.values()), List.copyOf(BASE.ore().keySet()));
        assertEquals(List.of(Band.values()), List.copyOf(BASE.gravel().keySet()));
        assertEquals(List.of(Band.values()), List.copyOf(BASE.clay().keySet()));
        for (Band band : Band.values()) {
            assertEquals(List.of(Rarity.values()), List.copyOf(BASE.ore().get(band).keySet()));
        }
    }
}
