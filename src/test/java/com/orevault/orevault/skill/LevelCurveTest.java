package com.orevault.orevault.skill;

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
}
