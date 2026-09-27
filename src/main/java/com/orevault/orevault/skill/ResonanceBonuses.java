package com.orevault.orevault.skill;

/**
 * Every node's percentage contribution to one ore break's Resonance, accumulated (§11).
 *
 * <p>{@code DropPipeline} already settled this shape for item drops — stages contribute, the
 * pipeline applies the total once — and the Resonance award has the same problem. Without one
 * accumulator each node patches the award method and the composition order becomes whatever the
 * last author assumed.</p>
 *
 * <h2>Why bonuses add rather than multiply</h2>
 *
 * <p>Every §6.1 node that touches this is worded "+X% Resonance", and there are enough of them —
 * Deep Habit, Calloused Hands, Kindred Rock, Vein Discipline, Molten Seam, the completion group —
 * that multiplying would compound far past a curve calibrated for a 100-hour progression. Summing
 * keeps a node's stated number meaning what it says wherever it sits in the tree: +25% is a quarter
 * of the base rate whether it is the first bonus or the seventh.</p>
 *
 * <p>Multiplicative modifiers do exist — Tithe's 1.75x on a consumed block, §4.2 team scaling — and
 * they are applied outside this accumulator, at the points §4.2 names. This is the additive layer
 * and nothing else.</p>
 */
public final class ResonanceBonuses {

    private double summed;

    /**
     * Adds one node's fractional bonus: {@code 0.25} for +25%.
     *
     * <p>Negative contributions are allowed, because tradeoff nodes subtract, but they cannot drive
     * the multiplier below zero — see {@link #multiplier()}.</p>
     */
    public void add(double fraction) {
        summed += fraction;
    }

    /** The summed bonus, for a tooltip that wants to show it as a percentage. */
    public double totalBonus() {
        return summed;
    }

    /** The factor to multiply the base rate by, floored at zero so no break ever pays negative. */
    public double multiplier() {
        return Math.max(0.0, 1.0 + summed);
    }
}
