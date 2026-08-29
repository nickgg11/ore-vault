package com.orevault.orevault.skill;

/**
 * Pure-logic calculator for the dynamic per-level Resonance/Animus thresholds
 * (§4.3 / §5.2).
 *
 * <p>The track is a fixed {@link NodeCosts#LEVEL_CAP} levels for both trees,
 * over which the total expected gain is distributed as an exponential curve in
 * which the last level costs {@value #LAST_TO_FIRST_RATIO} times the first.
 * Pools are cumulative and are never reset.</p>
 *
 * <p><b>Levels are decoupled from skill points.</b> Each level awards
 * {@link #pointsPerLevel()} = {@code ceil(totalTreeCost / LEVEL_CAP)} points, so
 * reaching the cap grants at least enough to buy the whole tree. Adding or
 * removing nodes changes the award, not the cap — which is what keeps the level
 * requirements in §6 (highest: 30) meaningful. The earlier design set the cap to
 * the tree's total cost, putting every gate in the mod inside the first hour of
 * a 100-hour curve.</p>
 *
 * <p>The curve is calibrated for a <b>solo</b> player; team size is handled on
 * the gain side by {@link TeamScaling}, not here.</p>
 */
public final class LevelCurve {

    /** Ratio between the most and least expensive level on the curve. */
    public static final double LAST_TO_FIRST_RATIO = 100.0;

    private final long[] levelCosts;
    private final int totalTreeCost;
    private final int pointsPerLevel;

    private LevelCurve(long[] levelCosts, int totalTreeCost, int pointsPerLevel) {
        this.levelCosts = levelCosts;
        this.totalTreeCost = totalTreeCost;
        this.pointsPerLevel = pointsPerLevel;
    }

    /**
     * Computes the level-cost curve.
     *
     * @param totalTreeCost   sum of every tier cost in the tree; sets the points awarded per level
     * @param soloGainPerHour average Resonance/Animus gained per hour by a solo player
     * @param targetPlayHours target hours to fully complete the tree
     */
    public static LevelCurve compute(int totalTreeCost, double soloGainPerHour, int targetPlayHours) {
        if (totalTreeCost < 1) {
            throw new IllegalArgumentException("totalTreeCost must be >= 1, got " + totalTreeCost);
        }
        if (soloGainPerHour <= 0 || targetPlayHours <= 0) {
            throw new IllegalArgumentException("soloGainPerHour and targetPlayHours must be positive");
        }

        int levelCap = NodeCosts.LEVEL_CAP;
        int pointsPerLevel = Math.ceilDiv(totalTreeCost, levelCap);
        double totalGain = soloGainPerHour * targetPlayHours;

        long[] costs = new long[levelCap];
        if (levelCap == 1) {
            costs[0] = Math.max(1, Math.round(totalGain));
            return new LevelCurve(costs, totalTreeCost, pointsPerLevel);
        }

        double growthFactor = Math.pow(LAST_TO_FIRST_RATIO, 1.0 / (levelCap - 1));
        double baseCost = totalGain * (growthFactor - 1.0) / (Math.pow(growthFactor, levelCap) - 1.0);

        long sum = 0;
        for (int i = 0; i < levelCap - 1; i++) {
            long cost = Math.max(1, Math.round(baseCost * Math.pow(growthFactor, i)));
            costs[i] = cost;
            sum += cost;
        }
        // The final level absorbs any rounding drift so the curve sums to the total gain.
        costs[levelCap - 1] = Math.max(1, Math.round(totalGain) - sum);
        return new LevelCurve(costs, totalTreeCost, pointsPerLevel);
    }

    /** Per-level costs; index 0 is the cost to go from level 0 to level 1. */
    public long[] levelCosts() {
        return levelCosts.clone();
    }

    /** Number of levels on the curve ({@link NodeCosts#LEVEL_CAP}). */
    public int levelCount() {
        return levelCosts.length;
    }

    /** Sum of every tier cost in the tree this curve was calibrated for. */
    public int totalTreeCost() {
        return totalTreeCost;
    }

    /** Skill points granted on each level-up: {@code ceil(totalTreeCost / LEVEL_CAP)} (§4.3). */
    public int pointsPerLevel() {
        return pointsPerLevel;
    }

    /**
     * Points a team holds at {@code level}, assuming none have been spent.
     * Always {@code >= totalTreeCost} at the cap, by construction of
     * {@link #pointsPerLevel()}.
     */
    public int pointsAtLevel(int level) {
        return pointsPerLevel * Math.min(Math.max(level, 0), levelCount());
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
