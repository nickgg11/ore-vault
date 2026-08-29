package com.orevault.orevault.resonance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.skill.LevelCurve;
import com.orevault.orevault.skill.NodeCosts;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.NodeDefs;
import com.orevault.orevault.skill.TeamScaling;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the §4.3 award rules.
 *
 * <p>Two of these guard regressions that would be invisible in play until much
 * later: a pool that resets on level-up looks fine for one level and then
 * silently doubles the grind, and awarding one level per gain instead of every
 * level crossed strands points whenever a big Vault Echo burst lands.</p>
 */
class ResonanceSystemTest {

    private static final UUID TEAM_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    /** Round numbers so thresholds read directly off the curve. */
    private static final double GAIN_PER_HOUR = 1000.0;
    private static final int TARGET_HOURS = 100;

    private static LevelCurve curve() {
        return LevelCurve.compute(NodeDefs.totalTreeCost(Tree.RESONANCE), GAIN_PER_HOUR, TARGET_HOURS);
    }

    private static OreVaultTeamData team() {
        return new OreVaultTeamData(TEAM_ID);
    }

    @Test
    void crossingOneThresholdAwardsPointsPerLevelAndKeepsThePool() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();
        long threshold = curve.cumulativeForLevel(1);

        ResonanceSystem.LevelUp result = ResonanceSystem.applyGain(data, curve, threshold, 1);

        assertTrue(result.leveledUp());
        assertEquals(0, result.previousLevel());
        assertEquals(1, result.newLevel());
        assertEquals(curve.pointsPerLevel(), result.pointsAwarded());
        assertEquals(curve.pointsPerLevel(), data.getResonanceSkillPoints());
        assertEquals(1, data.getResonanceLevel());
        // §4.3: the pool is never reset — it keeps accumulating past every threshold.
        assertEquals(threshold, data.getResonancePool());
    }

    @Test
    void aGainShortOfTheThresholdAwardsNothing() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();

        ResonanceSystem.LevelUp result = ResonanceSystem.applyGain(data, curve, curve.cumulativeForLevel(1) - 1, 1);

        assertFalse(result.leveledUp());
        assertEquals(0, result.pointsAwarded());
        assertEquals(0, data.getResonanceSkillPoints());
        assertEquals(0, data.getResonanceLevel());
    }

    @Test
    void oneGainCrossingSeveralThresholdsAwardsEveryLevelPassed() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();

        ResonanceSystem.LevelUp result = ResonanceSystem.applyGain(data, curve, curve.cumulativeForLevel(5), 1);

        assertEquals(5, result.newLevel());
        assertEquals(5 * curve.pointsPerLevel(), result.pointsAwarded());
        assertEquals(5 * curve.pointsPerLevel(), data.getResonanceSkillPoints());
    }

    @Test
    void gainsPastTheCapAccumulateInThePoolButAwardNoPoints() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();
        ResonanceSystem.applyGain(data, curve, curve.totalCost(), 1);

        assertEquals(NodeCosts.LEVEL_CAP, data.getResonanceLevel());
        int pointsAtCap = data.getResonanceSkillPoints();

        ResonanceSystem.LevelUp overflow = ResonanceSystem.applyGain(data, curve, 50_000, 1);

        assertFalse(overflow.leveledUp());
        assertEquals(0, overflow.pointsAwarded());
        assertEquals(pointsAtCap, data.getResonanceSkillPoints());
        assertEquals(NodeCosts.LEVEL_CAP, data.getResonanceLevel());
        assertEquals(curve.totalCost() + 50_000, data.getResonancePool());
    }

    @Test
    void successiveGainsAwardEachLevelExactlyOnce() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();
        long[] costs = curve.levelCosts();

        for (int i = 0; i < 3; i++) {
            ResonanceSystem.LevelUp result = ResonanceSystem.applyGain(data, curve, costs[i], 1);
            assertEquals(i + 1, result.newLevel());
            assertEquals(curve.pointsPerLevel(), result.pointsAwarded(), "level " + (i + 1));
        }
        assertEquals(3 * curve.pointsPerLevel(), data.getResonanceSkillPoints());
    }

    @Test
    void readoutsTrackThePoolNotTheCachedLevelField() {
        LevelCurve curve = curve();
        OreVaultTeamData data = team();
        ResonanceSystem.applyGain(data, curve, curve.cumulativeForLevel(3), 1);

        assertEquals(3, ResonanceSystem.levelOf(data, curve));
        assertEquals(0.0, ResonanceSystem.progressToNextLevel(data, curve), 0.0001);

        ResonanceSystem.applyGain(data, curve, curve.levelCosts()[3] / 2.0, 1);

        assertEquals(3, ResonanceSystem.levelOf(data, curve));
        assertEquals(0.5, ResonanceSystem.progressToNextLevel(data, curve), 0.01);
    }

    /**
     * The pool must go through {@link TeamScaling}, not a second copy of the
     * §4.2 formula — the whole point of the rebalance was that a team shares a
     * tree rather than progressing five times faster.
     */
    @Test
    void teamGainIsScaledBeforeItReachesThePool() {
        LevelCurve curve = curve();
        double summed = 1000.0;

        OreVaultTeamData solo = team();
        ResonanceSystem.applyGain(solo, curve, summed, 1);
        assertEquals(Math.round(summed), solo.getResonancePool(), "solo play is unscaled");

        for (int teamSize = 2; teamSize <= 5; teamSize++) {
            OreVaultTeamData data = team();
            ResonanceSystem.applyGain(data, curve, summed, teamSize);
            assertEquals(Math.round(TeamScaling.teamPoolGain(summed, teamSize)), data.getResonancePool(),
                    "team of " + teamSize);
            assertTrue(data.getResonancePool() < solo.getResonancePool() * teamSize,
                    "team of " + teamSize + " must not outpace the same players mining solo");
        }
    }
}
