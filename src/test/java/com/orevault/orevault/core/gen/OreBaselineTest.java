package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.Rarity;
import java.util.List;
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

    /** BAL-E4-6: the same counts over 246 layers, 0.4% thinner, and compared against that. */
    @Test
    void theExpandedStoneBandIsOneLayerThinner() {
        double ratio = BASE.density(Band.STONE, VaultVariant.EXPANDED) / BASE.density(Band.STONE, VaultVariant.BASE);
        assertEquals(245.0 / 246.0, ratio, 1e-12);
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
    @Test
    void theEdgeFactorsAreInTheBalancersRange() {
        for (Rarity rarity : Rarity.values()) {
            double stone = BASE.edgeFactor(Band.STONE, rarity, VaultVariant.BASE) - 1;
            double deepslate = BASE.edgeFactor(Band.DEEPSLATE, rarity, VaultVariant.EXPANDED) - 1;
            assertTrue(stone > 0.0035 && stone < 0.0070, rarity + " stone " + stone);
            assertTrue(deepslate > 0.0150 && deepslate < 0.0265, rarity + " deepslate " + deepslate);
        }
        assertTrue(BASE.edgeFactor(Band.DEEPSLATE, Rarity.COMMON, VaultVariant.EXPANDED)
                > BASE.edgeFactor(Band.DEEPSLATE, Rarity.RARE, VaultVariant.EXPANDED));
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
