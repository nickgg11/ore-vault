package com.orevault.orevault.skill;

/**
 * Pure-logic calculator for the dynamic per-level Resonance/Animus thresholds
 * (§4.3 / §5.2).
 *
 * <p>The total skill-point cost of a tree is distributed over that many levels
 * as an exponential curve in which the last level costs
 * {@value #LAST_TO_FIRST_RATIO} times the first. One skill point is awarded per
 * level reached; pools are cumulative and are never reset.</p>
 *
 * <p>The growth factor and base cost are derived from the total gain (per §4.3),
 * so adding or removing nodes automatically recalibrates the whole curve.</p>
 */
public final class LevelCurve {

    /** Ratio between the most and least expensive level on the curve. */
    public static final double LAST_TO_FIRST_RATIO = 100.0;

    private final long[] levelCosts;

    private LevelCurve(long[] levelCosts) {
        this.levelCosts = levelCosts;
    }

    /**
     * Computes the level-cost curve.
     *
     * @param totalTreeCost    sum of every tier cost in the tree; also the level count
     * @param baseGainPerHour  average Resonance/Animus gained per hour by a solo player
     * @param assumedTeamSize  team size used for calibration (multiplier per §4.2)
     * @param targetPlayHours  target hours to fully complete the tree
     */
    public static LevelCurve compute(int totalTreeCost, double baseGainPerHour, double assumedTeamSize, int targetPlayHours) {
        if (totalTreeCost < 1) {
            throw new IllegalArgumentException("totalTreeCost must be >= 1, got " + totalTreeCost);
        }
        if (baseGainPerHour <= 0 || assumedTeamSize < 1 || targetPlayHours <= 0) {
            throw new IllegalArgumentException(
                    "baseGainPerHour, assumedTeamSize and targetPlayHours must be positive");
        }

        double multiplier = 1.0 + (assumedTeamSize - 1.0) * NodeCosts.TEAM_SIZE_MULTIPLIER_STEP;
        double totalGain = baseGainPerHour * multiplier * targetPlayHours;

        long[] costs = new long[totalTreeCost];
        if (totalTreeCost == 1) {
            costs[0] = Math.max(1, Math.round(totalGain));
            return new LevelCurve(costs);
        }

        double growthFactor = Math.pow(LAST_TO_FIRST_RATIO, 1.0 / (totalTreeCost - 1));
        double baseCost = totalGain * (growthFactor - 1.0) / (Math.pow(growthFactor, totalTreeCost) - 1.0);

        long sum = 0;
        for (int i = 0; i < totalTreeCost - 1; i++) {
            long cost = Math.max(1, Math.round(baseCost * Math.pow(growthFactor, i)));
            costs[i] = cost;
            sum += cost;
        }
        // The final level absorbs any rounding drift so the curve sums to the total gain.
        costs[totalTreeCost - 1] = Math.max(1, Math.round(totalGain) - sum);
        return new LevelCurve(costs);
    }

    /** Per-level costs; index 0 is the cost to go from level 0 to level 1. */
    public long[] levelCosts() {
        return levelCosts.clone();
    }

    /** Number of levels on the curve (equals the tree's total skill-point cost). */
    public int levelCount() {
        return levelCosts.length;
    }

    /** Cumulative Resonance/Animus required to reach {@code level}. */
    public long cumulativeForLevel(int level) {
        long total = 0;
        for (int i = 0; i < Math.min(Math.max(level, 0), levelCosts.length); i++) {
            total += levelCosts[i];
        }
        return total;
    }

    /** Total cumulative cost to fully max the curve. */
    public long totalCost() {
        return cumulativeForLevel(levelCount());
    }

    /** Current level (0..levelCount) for a cumulative pool value. */
    public int levelFor(long cumulative) {
        int level = 0;
        long acc = 0;
        for (long cost : levelCosts) {
            acc += cost;
            if (cumulative < acc) {
                return level;
            }
            level++;
        }
        return level;
    }

    /** Progress (0..1) within the current level toward the next one; 1.0 when maxed. */
    public double progressWithinLevel(long cumulative) {
        int level = levelFor(cumulative);
        if (level >= levelCosts.length) {
            return 1.0;
        }
        long spent = cumulative - cumulativeForLevel(level);
        return (double) spent / levelCosts[level];
    }
}
