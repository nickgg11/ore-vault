package com.orevault.orevault.core.curve;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.BandBounds;
import com.orevault.orevault.core.gen.DensityTally;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.OrePickers;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import org.junit.jupiter.api.Test;

/**
 * The figures §4.3 and S11 state, restated from the Java curve to within 1% of their unrounded values (§4.3 "What gates
 * node effects before T14", condition 2). If the baseline, the mix or a Resonance value moves, this fails, and the spec's
 * figures are restated in the same PR. The named layers are closed form before overlap, as §3.1's slab table is: the
 * expected slab density divided by one minus the stone band's overlap.
 */
class CurveSpecFiguresTest {
    private static final double ONE_PERCENT = 0.01;
    private static final OreBaseline BASE = OreBaseline.BASE;
    private static final LevelCurve CURVE = LevelCurve.derive(BASE, 100, 1.0);
    private static final double RESONANCE_PER_HOUR = LevelCurve.resonancePerHour(BASE);

    @Test
    void theBandAverage() {
        assertWithin(219.2, LevelCurve.oresPerHour(BASE), "ore an hour");
        assertWithin(763.8, RESONANCE_PER_HOUR, "Resonance an hour");
        assertWithin(76_383, CURVE.totalThousandths() / 1_000.0, "total Resonance");
        assertWithin(113.1, CURVE.costThousandths(1) / 1_000.0, "level 1's cost");
        assertWithin(11_312, CURVE.costThousandths(30) / 1_000.0, "level 30's cost");
        assertWithin(8.9, minutesTo(1, RESONANCE_PER_HOUR), "minutes to level 1");
        assertWithin(19.3, minutesTo(2, RESONANCE_PER_HOUR), "minutes to level 2");
    }

    @Test
    void theMilestones() {
        assertWithin(0.32, hoursTo(2), "hours to level 2");
        assertWithin(2.20, hoursTo(8), "hours to level 8");
        assertWithin(3.35, hoursTo(10), "hours to level 10");
        assertWithin(14.1, hoursTo(18), "hours to level 18");
        assertWithin(85.2, hoursTo(29), "hours to level 29");
        assertWithin(100.0, hoursTo(30), "hours to level 30");
    }

    @Test
    void theFirstStoneUnderTheDirt() {
        Layer layer = layer(65, 95);
        assertWithin(184.7, layer.oresPerHour(), "Y65..95 ore an hour");
        assertWithin(13.7, minutesTo(1, layer.resonancePerHour()), "Y65..95 minutes to level 1");
        assertWithin(29.7, minutesTo(2, layer.resonancePerHour()), "Y65..95 minutes to level 2");
    }

    @Test
    void theSeventeenToThirtyTwoSlab() {
        Layer layer = layer(17, 32);
        assertWithin(212.3, layer.oresPerHour(), "Y17..32 ore an hour");
        assertWithin(9.8, minutesTo(1, layer.resonancePerHour()), "Y17..32 minutes to level 1");
        assertWithin(21.4, minutesTo(2, layer.resonancePerHour()), "Y17..32 minutes to level 2");
    }

    @Test
    void theBottomSlab() {
        assertWithin(329.4, layer(1, 16).oresPerHour(), "Y1..16 ore an hour");
    }

    private record Layer(double oresPerHour, double resonancePerOre) {
        double resonancePerHour() {
            return oresPerHour * resonancePerOre;
        }
    }

    private static Layer layer(int bottomY, int topY) {
        DensityTally tally = new DensityTally(BASE, VaultVariant.BASE,
                OrePickers.forVariant(VaultVariant.BASE, VanillaOres.candidates()), 1);
        for (int slab = 0; slab < tally.slabCount(Band.STONE); slab++) {
            if (tally.slabBounds(Band.STONE, slab).equals(new BandBounds(bottomY, topY))) {
                double density = tally.expectedSlabDensity(Band.STONE, slab) / (1 - DensityTally.STONE_OVERLAP);
                double perOre = 0;
                for (Rarity rarity : Rarity.values()) {
                    perOre += tally.expectedSlabShare(Band.STONE, slab, rarity) * rarity.baseResonance();
                }
                return new Layer(LevelCurve.SOLO_BLOCKS_PER_HOUR * density, perOre);
            }
        }
        throw new AssertionError("No stone slab Y" + bottomY + ".." + topY);
    }

    private static double hoursTo(int level) {
        return CURVE.thresholdThousandths(level) / 1_000.0 / RESONANCE_PER_HOUR;
    }

    private static double minutesTo(int level, double resonancePerHour) {
        return CURVE.thresholdThousandths(level) / 1_000.0 / resonancePerHour * 60;
    }

    private static void assertWithin(double stated, double actual, String what) {
        assertTrue(Math.abs(actual - stated) <= ONE_PERCENT * Math.abs(actual),
                what + ": the spec states " + stated + ", the curve gives " + actual);
    }
}
