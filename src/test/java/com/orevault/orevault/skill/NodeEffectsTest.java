package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The numbers behind the §6.1 breakpoint, growth and keystone nodes.
 *
 * <p>Every one of these is a curve that reads fine on paper and goes wrong at an end: a lifetime
 * counter that runs away, a depth bonus that pays nothing, a streak that resets to zero. This is
 * the layer where that is catchable — none of it needs a world, and none of it is visible in play
 * until a number is already wrong.</p>
 */
class NodeEffectsTest {

    private static final double EPSILON = 1e-6;

    // ----- Calloused Hands: lifetime blocks, logarithmic -----

    @Test
    void callousedHandsPaysNothingBeforeItIsBought() {
        assertEquals(0.0, NodeEffects.callousedHandsBonus(0, 500_000), EPSILON);
    }

    @Test
    void callousedHandsPaysNothingOnAFreshAccount() {
        assertEquals(0.0, NodeEffects.callousedHandsBonus(3, 0), EPSILON);
    }

    @Test
    void callousedHandsMatchesTheCurveTheSpecTabulates() {
        // 10,000 blocks makes the logarithm's argument 2, so the bonus is the coefficient x ln 2.
        assertEquals(0.03 * Math.log(2), NodeEffects.callousedHandsBonus(1, 10_000), EPSILON);
        assertEquals(0.05 * Math.log(2), NodeEffects.callousedHandsBonus(2, 10_000), EPSILON);
        assertEquals(0.07 * Math.log(2), NodeEffects.callousedHandsBonus(3, 10_000), EPSILON);
    }

    @Test
    void callousedHandsStaysUnderThirtyPercentOverAFullProgression() {
        // 300k to 600k blocks is roughly a 100-hour run. The linear version this replaced reached
        // x30 over the same span, which is the failure this curve exists to avoid.
        assertTrue(NodeEffects.callousedHandsBonus(3, 600_000) < 0.30,
                "600k blocks pays " + NodeEffects.callousedHandsBonus(3, 600_000));
    }

    @Test
    void callousedHandsIsCappedNoMatterHowLongYouPlay() {
        assertEquals(NodeCosts.CALLOUSED_HANDS_CAP,
                NodeEffects.callousedHandsBonus(3, Long.MAX_VALUE / 2), EPSILON);
    }

    @Test
    void callousedHandsNeverDecreasesAsBlocksRise() {
        double previous = -1;
        for (long blocks = 0; blocks < 2_000_000; blocks += 25_000) {
            double bonus = NodeEffects.callousedHandsBonus(3, blocks);
            assertTrue(bonus >= previous, "bonus fell at " + blocks + " blocks");
            previous = bonus;
        }
    }

    // ----- Highwater Mark: depth record, paid in durability -----

    @Test
    void highwaterMarkPaysTheFlatRepairAtOrAboveTheBaseline() {
        assertEquals(1, NodeEffects.highwaterMarkRepair(1, NodeCosts.HIGHWATER_MARK_BASELINE_Y));
        assertEquals(1, NodeEffects.highwaterMarkRepair(1, 200));
    }

    @Test
    void highwaterMarkScalesWithHowDeepYouHaveEverBeen() {
        assertEquals(2, NodeEffects.highwaterMarkRepair(1, 48));
        assertEquals(5, NodeEffects.highwaterMarkRepair(1, 0));
    }

    @Test
    void highwaterMarkReachesItsStatedMaximumAtExpandedBedrock() {
        assertEquals(NodeCosts.HIGHWATER_MARK_MAX_REPAIR,
                NodeEffects.highwaterMarkRepair(1, NodeCosts.VAULT_EXPANDED_FLOOR_Y));
    }

    @Test
    void highwaterMarkPaysNothingUnbought() {
        assertEquals(0, NodeEffects.highwaterMarkRepair(0, -63));
    }

    @Test
    void onlyTierTwoSpillsRepairOntoArmour() {
        assertFalse(NodeEffects.highwaterMarkRepairsArmour(1));
        assertTrue(NodeEffects.highwaterMarkRepairsArmour(2));
    }

    // ----- Vein Discipline: a streak that decays rather than resets -----

    @Test
    void veinDisciplineBuildsTwoPercentAtATimeToItsCap() {
        assertEquals(0.0, NodeEffects.veinDisciplineBonus(0), EPSILON);
        assertEquals(0.02, NodeEffects.veinDisciplineBonus(1), EPSILON);
        assertEquals(0.30, NodeEffects.veinDisciplineBonus(15), EPSILON);
    }

    @Test
    void veinDisciplineDoesNotKeepPayingPastItsCap() {
        assertEquals(NodeEffects.veinDisciplineBonus(15), NodeEffects.veinDisciplineBonus(40), EPSILON);
    }

    @Test
    void abandoningAVeinCostsThreeStepsNotTheWholeStreak() {
        assertEquals(12, NodeEffects.veinDisciplineAfterAbandon(15));
        assertEquals(0, NodeEffects.veinDisciplineAfterAbandon(2), "the streak floors at zero");
        assertEquals(0, NodeEffects.veinDisciplineAfterAbandon(0));
    }

    // ----- Novice's Luck: a growth node that retires itself -----

    @Test
    void novicesLuckIsLargeEarlyAndExactlyZeroAtTheLevelCap() {
        assertEquals(0.28, NodeEffects.novicesLuckBonus(2), EPSILON);
        assertEquals(0.0, NodeEffects.novicesLuckBonus(NodeCosts.LEVEL_CAP), EPSILON);
    }

    @Test
    void novicesLuckNeverGoesNegative() {
        assertEquals(0.0, NodeEffects.novicesLuckBonus(NodeCosts.LEVEL_CAP + 10), EPSILON);
    }

    // ----- Deep Habit and Long Delve: within-trip, so they cannot compound -----

    @Test
    void deepHabitStepsEveryThousandBlocksAndStops() {
        assertEquals(0.0, NodeEffects.deepHabitBonus(999), EPSILON);
        assertEquals(0.05, NodeEffects.deepHabitBonus(1_000), EPSILON);
        assertEquals(0.10, NodeEffects.deepHabitBonus(2_500), EPSILON);
        assertEquals(NodeCosts.DEEP_HABIT_CAP, NodeEffects.deepHabitBonus(1_000_000), EPSILON);
    }

    @Test
    void longDelveStacksOncePerTwentyMinutesUpToItsTierCap() {
        assertEquals(0, NodeEffects.longDelveStacks(1, 19));
        assertEquals(1, NodeEffects.longDelveStacks(1, 20));
        assertEquals(2, NodeEffects.longDelveStacks(1, 300), "tier 1 caps at 2 stacks");
        assertEquals(4, NodeEffects.longDelveStacks(2, 300), "tier 2 caps at 4");
        assertEquals(0, NodeEffects.longDelveStacks(0, 300));
    }

    // ----- Stonecutter's Patience -----

    @Test
    void stonecuttersPatiencePaysOneXpPerTenThousandStoneUpToItsTierCap() {
        assertEquals(0, NodeEffects.stonecuttersPatienceXp(1, 9_999));
        assertEquals(2, NodeEffects.stonecuttersPatienceXp(1, 25_000));
        assertEquals(3, NodeEffects.stonecuttersPatienceXp(1, 90_000), "tier 1 caps at +3");
        assertEquals(5, NodeEffects.stonecuttersPatienceXp(2, 90_000), "tier 2 caps at +5");
        assertEquals(0, NodeEffects.stonecuttersPatienceXp(0, 90_000));
    }

    // ----- Bedrock Communion -----

    @Test
    void bedrockCommunionReachesItsStatedCeilingAtTheVaultFloor() {
        assertEquals(1.0 + 1.26, NodeEffects.bedrockCommunionYieldMultiplier(
                NodeCosts.VAULT_EXPANDED_FLOOR_Y), EPSILON);
    }

    @Test
    void bedrockCommunionGivesNoBonusAtOrAboveZero() {
        assertEquals(1.0, NodeEffects.bedrockCommunionYieldMultiplier(0), EPSILON);
        assertEquals(1.0, NodeEffects.bedrockCommunionYieldMultiplier(200), EPSILON);
    }

    @Test
    void bedrockCommunionPenalisesTheStoneBandAndNothingElse() {
        assertTrue(NodeEffects.bedrockCommunionPenalised(32));
        assertTrue(NodeEffects.bedrockCommunionPenalised(200));
        assertTrue(NodeEffects.bedrockCommunionPenalised(NodeCosts.VAULT_DIRT_BAND_MIN_Y - 1));

        assertFalse(NodeEffects.bedrockCommunionPenalised(31), "the deep half is the reward, not the cost");
        assertFalse(NodeEffects.bedrockCommunionPenalised(-40));
    }

    @Test
    void bedrockCommunionLeavesTheBuildableSurfaceAlone() {
        // Y=246 is the base of the dirt band. Everything a player builds on sits at or above it,
        // and taxing that is what made the first version of this keystone unplayable.
        assertFalse(NodeEffects.bedrockCommunionPenalised(NodeCosts.VAULT_DIRT_BAND_MIN_Y));
        assertFalse(NodeEffects.bedrockCommunionPenalised(300));
        assertEquals(1.0, NodeEffects.bedrockCommunionMiningSpeedFactor(250), EPSILON);
    }

    @Test
    void bedrockCommunionHalvesMiningSpeedOnlyInsideThePenaltyBand() {
        assertEquals(0.5, NodeEffects.bedrockCommunionMiningSpeedFactor(100), EPSILON);
        assertEquals(1.0, NodeEffects.bedrockCommunionMiningSpeedFactor(-10), EPSILON);
    }
}
