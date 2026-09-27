package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * How two node bonuses on the same quantity combine (§11).
 *
 * <p>The drop pipeline settled this for item drops: stages contribute and the total is applied
 * once. Resonance and mining speed have the same problem and now have the same answer, and these
 * tests are what makes it structural rather than whatever the next node's author assumed.</p>
 *
 * <p>The choice is stated in {@link ResonanceBonuses} and {@link MiningSpeedModifiers}: percentage
 * bonuses <em>sum</em>, penalties <em>multiply</em>, and the sum is applied before the product.</p>
 */
class EffectStackTest {

    private static final double EPSILON = 1.0e-9;

    // ----- Resonance -----

    @Test
    void noBonusLeavesTheBaseRateAlone() {
        assertEquals(1.0, new ResonanceBonuses().multiplier(), EPSILON);
    }

    @Test
    void twoResonanceBonusesAdd() {
        // Deep Habit at +25% and Calloused Hands at +12% pay +37%, not +40%. Multiplying
        // them is how a dozen small nodes quietly outrun a curve calibrated for 100 hours.
        ResonanceBonuses bonuses = new ResonanceBonuses();

        bonuses.add(0.25);
        bonuses.add(0.12);

        assertEquals(1.37, bonuses.multiplier(), EPSILON);
    }

    @Test
    void aNegativeContributionIsAllowedButCannotInvertTheAward() {
        // Tradeoff nodes subtract. A multiplier below zero would pay negative Resonance.
        ResonanceBonuses bonuses = new ResonanceBonuses();

        bonuses.add(-2.0);

        assertEquals(0.0, bonuses.multiplier(), EPSILON);
    }

    @Test
    void zeroContributionsAreIgnoredRatherThanAccumulated() {
        ResonanceBonuses bonuses = new ResonanceBonuses();

        bonuses.add(0.0);
        bonuses.add(0.5);

        assertEquals(1.5, bonuses.multiplier(), EPSILON);
    }

    // ----- mining speed -----

    @Test
    void miningSpeedWithNoModifiersIsTheVanillaSpeed() {
        assertEquals(4.0f, new MiningSpeedModifiers().apply(4.0f), 1.0e-6f);
    }

    @Test
    void miningSpeedBonusesAdd() {
        // Two Long Delve stacks (+10% each) and Kindred Rock (+20%) pay +40%.
        MiningSpeedModifiers speed = new MiningSpeedModifiers();

        speed.addBonus(0.10);
        speed.addBonus(0.10);
        speed.addBonus(0.20);

        assertEquals(1.4f, speed.apply(1.0f), 1.0e-6f);
    }

    @Test
    void aPenaltyAppliesToTheBoostedSpeedRatherThanTheBaseSpeed() {
        // Bedrock Communion halves mining speed in its penalty band. Halving after the
        // bonuses means the bonuses still help up there; halving the base first would let
        // enough stacked bonuses cancel the penalty out entirely.
        MiningSpeedModifiers speed = new MiningSpeedModifiers();

        speed.addBonus(1.0);
        speed.multiply(0.5);

        assertEquals(1.0f, speed.apply(1.0f), 1.0e-6f);
    }

    @Test
    void twoPenaltiesMultiply() {
        MiningSpeedModifiers speed = new MiningSpeedModifiers();

        speed.multiply(0.5);
        speed.multiply(0.5);

        assertEquals(0.25f, speed.apply(1.0f), 1.0e-6f);
    }

    @Test
    void speedNeverGoesNegative() {
        MiningSpeedModifiers speed = new MiningSpeedModifiers();

        speed.addBonus(-3.0);

        assertEquals(0.0f, speed.apply(4.0f), 1.0e-6f);
    }
}
