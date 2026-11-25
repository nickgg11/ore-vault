package com.orevault.orevault.core.curve;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.tree.TreeConstants;
import java.util.Arrays;

/**
 * The Resonance level curve (§4.3), computed once at server start. The rate is derived from the baseline the generator
 * reads, never chosen beside it: blocks an hour times the stone band's closed-form density gives ore an hour, and the
 * stone band's rarity shares times each rarity's base Resonance give Resonance an ore. If the density or the mix moves, the
 * curve moves with it. That is the 2026-09-27 lesson: the first build priced a curve for a Vault nobody had generated.
 *
 * <p>Thresholds are in thousandths of a Resonance, the pool's unit (ADR 0005 section 3), so the award compares longs. The
 * total is rounded once to thousandths, each level but the last is rounded to thousandths, and the last level takes the
 * drift, so the costs sum to the total exactly (§4.3 step 6).
 */
public final class LevelCurve {
    /**
     * Blocks a solo player breaks an hour, tunnelling with the E4-S5 rig, travel and inventory counted (§4.3 step 4). A
     * derivation, not a measurement, until the timed session at T14 (E5-S7, #287) measures it. A constant, not config: an
     * admin cannot know the right value, and the knob they want is {@code curve_divisor}.
     */
    public static final double SOLO_BLOCKS_PER_HOUR = 7_200;
    /** The last level costs this many times the first (§4.3 step 6). */
    public static final double LAST_TO_FIRST_RATIO = 100;

    private static final long THOUSANDTHS = 1_000L;

    /** Index 0 is level 1. */
    private final long[] costs;
    /** Index 0 is the pool needed for level 1. */
    private final long[] thresholds;

    private LevelCurve(long[] costs) {
        this.costs = costs;
        this.thresholds = new long[costs.length];
        long running = 0;
        for (int i = 0; i < costs.length; i++) {
            if (costs[i] < 1) {
                throw new IllegalArgumentException("Level " + (i + 1) + " would cost " + costs[i] + " thousandths");
            }
            running = Math.addExact(running, costs[i]);
            thresholds[i] = running;
        }
    }

    /** Ore a solo player breaks an hour in the base type's stone band, before overlap between veins (§4.3 step 4). */
    public static double oresPerHour(OreBaseline baseline) {
        return SOLO_BLOCKS_PER_HOUR * baseline.density(Band.STONE, VaultVariant.BASE);
    }

    /** Mean base Resonance of one ore in the stone band, weighted by the placed-block shares the generator places. */
    public static double resonancePerOre(OreBaseline baseline) {
        double sum = 0;
        for (Rarity rarity : Rarity.values()) {
            sum += baseline.share(Band.STONE, rarity) * rarity.baseResonance();
        }
        return sum;
    }

    /** Resonance a solo player earns an hour with nothing bought (§4.3 step 4). */
    public static double resonancePerHour(OreBaseline baseline) {
        return oresPerHour(baseline) * resonancePerOre(baseline);
    }

    /** {@code LAST_TO_FIRST_RATIO ^ (1 / (LEVEL_CAP - 1))}, about 1.172. */
    public static double growthFactor() {
        return Math.pow(LAST_TO_FIRST_RATIO, 1.0 / (TreeConstants.LEVEL_CAP - 1));
    }

    /**
     * §4.3 steps 4 to 6. The divisor is applied to the total, before the distribution, so it scales every threshold by one
     * factor and keeps the curve's shape.
     *
     * @param targetPlayHours {@code [resonance].target_play_hours}
     * @param curveDivisor {@code [resonance].curve_divisor}
     */
    public static LevelCurve derive(OreBaseline baseline, double targetPlayHours, double curveDivisor) {
        if (!(targetPlayHours > 0) || !(curveDivisor > 0) || Double.isInfinite(targetPlayHours)
                || Double.isInfinite(curveDivisor)) {
            throw new IllegalArgumentException("target_play_hours " + targetPlayHours + ", curve_divisor " + curveDivisor);
        }
        double totalResonance = resonancePerHour(baseline) * targetPlayHours / curveDivisor;
        return ofTotal(Math.round(totalResonance * THOUSANDTHS));
    }

    /** Spreads a total over the levels as {@code baseCost * growthFactor ^ n}, the last level absorbing the drift. */
    static LevelCurve ofTotal(long totalThousandths) {
        int levels = TreeConstants.LEVEL_CAP;
        double growth = growthFactor();
        double baseCost = totalThousandths * (growth - 1) / (Math.pow(growth, levels) - 1);
        long[] costs = new long[levels];
        long sum = 0;
        for (int n = 0; n < levels - 1; n++) {
            costs[n] = Math.round(baseCost * Math.pow(growth, n));
            sum += costs[n];
        }
        costs[levels - 1] = totalThousandths - sum;
        return new LevelCurve(costs);
    }

    public int levelCap() {
        return costs.length;
    }

    /** The whole curve: the pool at the level cap. */
    public long totalThousandths() {
        return thresholds[thresholds.length - 1];
    }

    /** What {@code level} costs on its own, counted from the level before it. */
    public long costThousandths(int level) {
        checkLevel(level, 1);
        return costs[level - 1];
    }

    /** The pool a team needs to have reached {@code level}; 0 for level 0. */
    public long thresholdThousandths(int level) {
        checkLevel(level, 0);
        return level == 0 ? 0 : thresholds[level - 1];
    }

    /** The highest level a pool has reached, 0 to the cap. */
    public int levelFor(long poolThousandths) {
        int level = 0;
        while (level < thresholds.length && poolThousandths >= thresholds[level]) {
            level++;
        }
        return level;
    }

    private void checkLevel(int level, int lowest) {
        if (level < lowest || level > costs.length) {
            throw new IllegalArgumentException("No level " + level + " on a curve of " + costs.length);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LevelCurve curve && Arrays.equals(costs, curve.costs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(costs);
    }

    @Override
    public String toString() {
        return "LevelCurve" + Arrays.toString(costs);
    }
}
