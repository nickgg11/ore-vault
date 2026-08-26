package com.orevault.orevault.skill;

import com.orevault.orevault.OreVault;

import java.util.Arrays;

/**
 * Dynamically calculates the level threshold curve at server start (design spec section 4.3).
 * One skill point is awarded per level; the total number of levels equals the total skill
 * point cost of the tree, so the curve automatically recalibrates whenever nodes are
 * added/removed or costs change.
 *
 * <p>Curve: {@code levelCost(n) = baseCost * growthFactor^n}, with baseCost and growthFactor
 * derived so the sum over all levels equals the total Resonance/Animus needed for the
 * target play time. Early levels are cheap; late levels are expensive.
 */
public final class LevelCurve {
    private final long[] cumulativeThresholds; // cumulative[i] = pool needed to reach level i+1
    private final long[] levelCosts;
    private final int maxLevel;

    private LevelCurve(long[] levelCosts) {
        this.levelCosts = levelCosts;
        this.maxLevel = levelCosts.length;
        this.cumulativeThresholds = new long[levelCosts.length];
        long running = 0;
        for (int i = 0; i < levelCosts.length; i++) {
            running += levelCosts[i];
            cumulativeThresholds[i] = running;
        }
    }

    public static LevelCurve compute(int totalTreeCost, double targetPlayHours,
                                     double assumedTeamSize, double avgPerHour, double weightPerUnit) {
        if (totalTreeCost <= 0) {
            return new LevelCurve(new long[]{Long.MAX_VALUE});
        }
        double effectiveMultiplier = 1 + (assumedTeamSize - 1) * NodeCosts.TEAM_MULTIPLIER_STEP;
        double perHour = avgPerHour * weightPerUnit * effectiveMultiplier;
        double total = perHour * targetPlayHours;

        // Solve for growthFactor: sum(g^n for n in 0..totalTreeCost-1) * base = total
        double growth = 1.04;
        double hi = 1.15, lo = 1.001;
        for (int iter = 0; iter < 200; iter++) {
            double sum = geometricSum(growth, totalTreeCost);
            double scaled = total / sum; // implied base
            if (Double.isNaN(scaled) || scaled < 1.0) {
                // base cost below 1 unit — shrink growth until last level is affordable
                hi = growth;
            } else {
                lo = growth;
            }
            growth = (lo + hi) / 2;
        }

        double sum = geometricSum(growth, totalTreeCost);
        double base = total / sum;
        long[] costs = new long[totalTreeCost];
        for (int n = 0; n < totalTreeCost; n++) {
            costs[n] = Math.max(1, Math.round(base * Math.pow(growth, n)));
        }
        OreVault.LOGGER.info("Ore Vault level curve: {} levels, base={}, growth={}, first={}, last={}",
                totalTreeCost, Math.round(base), growth, costs[0], costs[totalTreeCost - 1]);
        return new LevelCurve(costs);
    }

    private static double geometricSum(double growth, int terms) {
        double sum = 0;
        double term = 1;
        for (int i = 0; i < terms; i++) {
            sum += term;
            term *= growth;
        }
        return sum;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public long levelCost(int level) {
        return level >= 1 && level <= maxLevel ? levelCosts[level - 1] : Long.MAX_VALUE;
    }

    /** Level for a given pool amount (levels are 1-based; 0 = no level yet). */
    public int levelForPool(long pool) {
        int level = 0;
        for (long threshold : cumulativeThresholds) {
            if (pool >= threshold) {
                level++;
            } else {
                break;
            }
        }
        return level;
    }

    public long thresholdForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (level > maxLevel) {
            return Long.MAX_VALUE;
        }
        return cumulativeThresholds[level - 1];
    }

    /** Progress toward the next level in [0, 1]. */
    public float progressToNext(long pool, int currentLevel) {
        if (currentLevel >= maxLevel) {
            return 1.0F;
        }
        long prev = thresholdForLevel(currentLevel);
        long next = thresholdForLevel(currentLevel + 1);
        if (next <= prev) {
            return 1.0F;
        }
        return Math.min(1.0F, (float) (pool - prev) / (float) (next - prev));
    }

    public long[] levelCosts() {
        return Arrays.copyOf(levelCosts, levelCosts.length);
    }
}
