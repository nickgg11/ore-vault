package com.orevault.orevault.core.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.tree.TreeConstants;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** §4.3's curve: its shape, its total, the divisor, and that the rate comes from the baseline record. */
class LevelCurveTest {
    private static final LevelCurve DEFAULT = LevelCurve.derive(OreBaseline.BASE, 100, 1.0);

    @Test
    void thirtyLevels() {
        assertEquals(TreeConstants.LEVEL_CAP, DEFAULT.levelCap());
        assertEquals(30, DEFAULT.levelCap());
    }

    @Test
    void theCostsSumToTheTotalExactly() {
        long expected = Math.round(LevelCurve.resonancePerHour(OreBaseline.BASE) * 100 * 1_000);
        long sum = 0;
        for (int level = 1; level <= DEFAULT.levelCap(); level++) {
            sum += DEFAULT.costThousandths(level);
        }
        assertEquals(expected, sum);
        assertEquals(expected, DEFAULT.totalThousandths());
        assertEquals(expected, DEFAULT.thresholdThousandths(30));
    }

    @Test
    void theLastLevelCostsAHundredTimesTheFirst() {
        double ratio = (double) DEFAULT.costThousandths(30) / DEFAULT.costThousandths(1);
        assertEquals(100.0, ratio, 0.01);
    }

    @Test
    void eachLevelCostsTheGrowthFactorTimesTheOneBefore() {
        double growth = LevelCurve.growthFactor();
        assertEquals(Math.pow(100, 1.0 / 29), growth, 1e-12);
        for (int level = 2; level <= 30; level++) {
            double ratio = (double) DEFAULT.costThousandths(level) / DEFAULT.costThousandths(level - 1);
            assertEquals(growth, ratio, 1e-4, "level " + level);
        }
    }

    @Test
    void theDivisorScalesEveryThresholdByOneFactor() {
        LevelCurve halved = LevelCurve.derive(OreBaseline.BASE, 100, 2.0);
        LevelCurve doubled = LevelCurve.derive(OreBaseline.BASE, 100, 0.5);
        for (int level = 1; level <= 30; level++) {
            long base = DEFAULT.thresholdThousandths(level);
            // Each threshold is a sum of up to 30 costs rounded to a thousandth, so two curves may differ by that much.
            assertEquals(base / 2.0, halved.thresholdThousandths(level), 30, "level " + level);
            assertEquals(base * 2.0, doubled.thresholdThousandths(level), 30, "level " + level);
        }
    }

    @Test
    void theDivisorActsOnTheTotalLikeTheHours() {
        assertEquals(LevelCurve.derive(OreBaseline.BASE, 50, 1.0), LevelCurve.derive(OreBaseline.BASE, 100, 2.0));
    }

    @Test
    void theRateIsTheBaselineDensityTimesTheMixTimesTheResonanceValues() {
        OreBaseline base = OreBaseline.BASE;
        double expectedPerOre = 0;
        for (Rarity rarity : Rarity.values()) {
            expectedPerOre += base.share(Band.STONE, rarity) * rarity.baseResonance();
        }
        assertEquals(expectedPerOre, LevelCurve.resonancePerOre(base), 1e-12);
        assertEquals(7_200 * base.density(Band.STONE, com.orevault.orevault.core.gen.VaultVariant.BASE),
                LevelCurve.oresPerHour(base), 1e-9);
        // §4.3: 68/25/7 at 2, 5 and 12.5 is about 3.485 Resonance an ore, and 3.04% of 7,200 is about 219 ore an hour.
        assertRelative(3.485, LevelCurve.resonancePerOre(base), 0.001);
        assertRelative(219.2, LevelCurve.oresPerHour(base), 0.001);
    }

    @Test
    void theCurveMovesWithTheDensity() {
        OreBaseline twice = scaledStone(2.0);
        LevelCurve curve = LevelCurve.derive(twice, 100, 1.0);
        assertRelative(2 * DEFAULT.totalThousandths(), curve.totalThousandths(), 1e-9);
    }

    @Test
    void levelForCountsReachedThresholds() {
        assertEquals(0, DEFAULT.levelFor(0));
        assertEquals(0, DEFAULT.levelFor(DEFAULT.thresholdThousandths(1) - 1));
        assertEquals(1, DEFAULT.levelFor(DEFAULT.thresholdThousandths(1)));
        assertEquals(2, DEFAULT.levelFor(DEFAULT.thresholdThousandths(2)));
        assertEquals(29, DEFAULT.levelFor(DEFAULT.thresholdThousandths(30) - 1));
        assertEquals(30, DEFAULT.levelFor(DEFAULT.thresholdThousandths(30)));
        assertEquals(30, DEFAULT.levelFor(Long.MAX_VALUE));
        assertEquals(0, DEFAULT.levelFor(-1));
    }

    @Test
    void thresholdOfLevelZeroIsZeroAndOutOfRangeLevelsThrow() {
        assertEquals(0, DEFAULT.thresholdThousandths(0));
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.thresholdThousandths(31));
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.thresholdThousandths(-1));
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.costThousandths(0));
        assertThrows(IllegalArgumentException.class, () -> DEFAULT.costThousandths(31));
    }

    @Test
    void theConfigRangeEndsGiveAWholeCurve() {
        LevelCurve shortest = LevelCurve.derive(OreBaseline.BASE, 1, 100);
        LevelCurve longest = LevelCurve.derive(OreBaseline.BASE, 10_000, 0.01);
        for (LevelCurve curve : new LevelCurve[] {shortest, longest}) {
            for (int level = 1; level <= 30; level++) {
                assertTrue(curve.costThousandths(level) >= 1, curve + " level " + level);
                assertTrue(curve.thresholdThousandths(level) > curve.thresholdThousandths(level - 1));
            }
        }
    }

    @Test
    void aNonPositiveOrInfiniteInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.derive(OreBaseline.BASE, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.derive(OreBaseline.BASE, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.derive(OreBaseline.BASE, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class,
                () -> LevelCurve.derive(OreBaseline.BASE, Double.POSITIVE_INFINITY, 1));
    }

    private static OreBaseline scaledStone(double factor) {
        OreBaseline base = OreBaseline.BASE;
        Map<Band, Map<Rarity, OreBaseline.Veins>> ore = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            Map<Rarity, OreBaseline.Veins> byRarity = new EnumMap<>(Rarity.class);
            for (Rarity rarity : Rarity.values()) {
                OreBaseline.Veins veins = base.veins(band, rarity);
                double perChunk = band == Band.STONE ? veins.perChunk() * factor : veins.perChunk();
                byRarity.put(rarity, new OreBaseline.Veins(perChunk, veins.minSize(), veins.maxSize()));
            }
            ore.put(band, byRarity);
        }
        return new OreBaseline(ore, base.gravel(), base.clay());
    }

    private static void assertRelative(double expected, double actual, double tolerance) {
        assertTrue(Math.abs(actual - expected) <= tolerance * Math.abs(expected),
                actual + " is not within " + tolerance * 100 + "% of " + expected);
    }
}
