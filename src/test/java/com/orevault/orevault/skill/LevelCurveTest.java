package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.skill.NodeDef.Tree;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the §4.3 level curve.
 *
 * <p>The pacing assertions are the point of this suite: the whole defect the
 * rebalance fixed was that level gates opened far too early, and only a curve
 * calibrated over {@link NodeCosts#LEVEL_CAP} levels keeps the §6 level
 * requirements meaningful.</p>
 */
class LevelCurveTest {

    /** Round numbers so "hours in" reads directly off the cumulative pool. */
    private static final double GAIN_PER_HOUR = 1000.0;
    private static final int TARGET_HOURS = 100;

    private static LevelCurve resonanceCurve() {
        return LevelCurve.compute(NodeDefs.totalTreeCost(Tree.RESONANCE), GAIN_PER_HOUR, TARGET_HOURS);
    }

    /** Hours of solo play needed to reach {@code level}. */
    private static double hoursToReach(LevelCurve curve, int level) {
        return curve.cumulativeForLevel(level) / GAIN_PER_HOUR;
    }

    @Test
    void trackLengthIsTheLevelCapNotTheTreeCost() {
        LevelCurve curve = resonanceCurve();
        assertEquals(NodeCosts.LEVEL_CAP, curve.levelCount());
        // The bug this replaced: level count == total tree cost (225 for Resonance).
        assertTrue(curve.totalTreeCost() > curve.levelCount());
    }

    @Test
    void reachingTheCapGrantsEnoughPointsToBuyTheWholeTree() {
        for (Tree tree : Tree.values()) {
            int totalCost = NodeDefs.totalTreeCost(tree);
            LevelCurve curve = LevelCurve.compute(totalCost, GAIN_PER_HOUR, TARGET_HOURS);
            assertEquals(Math.ceilDiv(totalCost, NodeCosts.LEVEL_CAP), curve.pointsPerLevel(), tree + " points per level");
            assertTrue(curve.pointsAtLevel(NodeCosts.LEVEL_CAP) >= totalCost, tree + " cannot afford its own tree at the cap");
            // ...with only the slack that rounding the award up forces: strictly under
            // one point per level, so a maxed team is never sitting on a spare level's worth.
            assertTrue(curve.pointsAtLevel(NodeCosts.LEVEL_CAP) - totalCost < NodeCosts.LEVEL_CAP, tree + " over-grants points");
        }
    }

    @Test
    void firstNodesArePurchasableWithinTheFirstHour() {
        LevelCurve curve = resonanceCurve();
        assertTrue(hoursToReach(curve, 2) < 1.0, "level 2 at " + hoursToReach(curve, 2) + "h");
    }

    @Test
    void midTreeBranchesOpenInTheFirstFiveHours() {
        LevelCurve curve = resonanceCurve();
        double atTen = hoursToReach(curve, 10);
        assertTrue(atTen > 1.0 && atTen < 5.0, "level 10 at " + atTen + "h, expected 1-5h");
        assertTrue(hoursToReach(curve, 8) < atTen, "curve must be monotonic");
    }

    @Test
    void vaultExpansionKeystoneSitsAroundFifteenHours() {
        LevelCurve curve = resonanceCurve();
        int keystoneLevel = NodeCosts.VAULT_EXPANSION_LEVEL_REQS[0];
        double hours = hoursToReach(curve, keystoneLevel);
        assertTrue(hours > 10.0 && hours < 20.0, "level " + keystoneLevel + " at " + hours + "h, expected ~15h");
    }

    @Test
    void theCurveSumsToExactlyTheTargetPlayHours() {
        LevelCurve curve = resonanceCurve();
        assertEquals(Math.round(GAIN_PER_HOUR * TARGET_HOURS), curve.totalCost());
        assertEquals((double) TARGET_HOURS, hoursToReach(curve, NodeCosts.LEVEL_CAP), 0.001);
    }

    @Test
    void lastLevelCostsAboutOneHundredTimesTheFirst() {
        long[] costs = resonanceCurve().levelCosts();
        double ratio = (double) costs[costs.length - 1] / costs[0];
        assertEquals(LevelCurve.LAST_TO_FIRST_RATIO, ratio, 1.0);
    }

    @Test
    void levelForAndProgressTrackTheThresholds() {
        LevelCurve curve = resonanceCurve();
        assertEquals(0, curve.levelFor(0));
        assertEquals(0, curve.levelFor(curve.cumulativeForLevel(1) - 1));
        assertEquals(1, curve.levelFor(curve.cumulativeForLevel(1)));
        assertEquals(NodeCosts.LEVEL_CAP, curve.levelFor(curve.totalCost()));
        assertEquals(NodeCosts.LEVEL_CAP, curve.levelFor(Long.MAX_VALUE / 2), "pool keeps accruing past the cap");

        assertEquals(0.0, curve.progressWithinLevel(0), 0.0001);
        assertEquals(1.0, curve.progressWithinLevel(curve.totalCost()), 0.0001);
    }

    @Test
    void rejectsNonPositiveCalibrationInputs() {
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.compute(0, GAIN_PER_HOUR, TARGET_HOURS));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.compute(225, 0.0, TARGET_HOURS));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.compute(225, GAIN_PER_HOUR, 0));
    }

    // ----- curve_divisor (§4.3 step 6, #24) -----

    /**
     * The divisor is the pack author's grind knob: it must move the absolute
     * cost of every level and nothing else. If it changed the shape, the §4.3
     * milestone table and every level requirement in §6 would silently mean
     * something different at a non-default setting.
     */
    @Test
    void curveDivisorScalesEveryThresholdUniformly() {
        int treeCost = NodeDefs.totalTreeCost(Tree.RESONANCE);
        long[] full = LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS, 1.0).levelCosts();
        long[] halved = LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS, 2.0).levelCosts();

        assertEquals(full.length, halved.length);
        for (int i = 0; i < full.length; i++) {
            assertEquals(full[i] / 2.0, halved[i], 1.0, "level " + (i + 1) + " cost");
        }
    }

    @Test
    void curveDivisorLeavesTheLastToFirstRatioUnchanged() {
        int treeCost = NodeDefs.totalTreeCost(Tree.RESONANCE);
        long[] halved = LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS, 2.0).levelCosts();

        double ratio = (double) halved[halved.length - 1] / halved[0];
        assertEquals(LevelCurve.LAST_TO_FIRST_RATIO, ratio, 1.0);
    }

    @Test
    void curveDivisorHalvesTheTotalGrind() {
        int treeCost = NodeDefs.totalTreeCost(Tree.RESONANCE);
        LevelCurve halved = LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS, 2.0);

        assertEquals(Math.round(GAIN_PER_HOUR * TARGET_HOURS / 2.0), halved.totalCost());
        // Points per level is a property of the tree, not of the grind knob.
        assertEquals(Math.ceilDiv(treeCost, NodeCosts.LEVEL_CAP), halved.pointsPerLevel());
    }

    @Test
    void defaultDivisorMatchesTheThreeArgumentCurve() {
        int treeCost = NodeDefs.totalTreeCost(Tree.RESONANCE);

        assertArrayEquals(
                LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS).levelCosts(),
                LevelCurve.compute(treeCost, GAIN_PER_HOUR, TARGET_HOURS, 1.0).levelCosts());
    }

    @Test
    void rejectsNonPositiveDivisor() {
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.compute(225, GAIN_PER_HOUR, TARGET_HOURS, 0.0));
        assertThrows(IllegalArgumentException.class, () -> LevelCurve.compute(225, GAIN_PER_HOUR, TARGET_HOURS, -1.0));
    }
}
