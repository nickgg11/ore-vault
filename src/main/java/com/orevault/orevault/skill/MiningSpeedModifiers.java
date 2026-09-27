package com.orevault.orevault.skill;

/**
 * Every node's contribution to how fast one block breaks, accumulated (§6.1).
 *
 * <p>Three nodes modify mining speed and they do not all pull the same way: Long Delve adds 10% per
 * Delve stack, Kindred Rock adds 20% on an attuned ore, and Bedrock Communion halves the lot inside
 * its penalty band. Three nodes silently combining in an undefined order is how a haste bug gets
 * found in a playtest instead of a test, so the order is fixed here and asserted in
 * {@code EffectStackTest}.</p>
 *
 * <h2>The order</h2>
 *
 * <p>Bonuses <b>sum</b>, penalties <b>multiply</b>, and the sum is applied first:
 * {@code base x (1 + sum of bonuses) x product of penalties}.</p>
 *
 * <p>Summing the bonuses is the same argument as {@link ResonanceBonuses}: +10% per stack should be
 * a tenth of the base speed whatever else is running. Applying the penalty last is what keeps
 * Bedrock Communion a real cost — halving the base first would let enough stacked bonuses cancel out
 * a penalty the design intends you to feel.</p>
 */
public final class MiningSpeedModifiers {

    private double bonus;
    private double penalty = 1.0;

    /** Adds a fractional speed bonus: {@code 0.10} for +10%. */
    public void addBonus(double fraction) {
        bonus += fraction;
    }

    /** Applies a multiplicative factor: {@code 0.5} to halve. */
    public void multiply(double factor) {
        penalty *= factor;
    }

    /** Whether anything at all was contributed, so a caller can skip a no-op write. */
    public boolean isEmpty() {
        return bonus == 0.0 && penalty == 1.0;
    }

    /** The modified speed, floored at zero — a negative break speed is not a thing. */
    public float apply(float baseSpeed) {
        double modified = baseSpeed * (1.0 + bonus) * penalty;
        return (float) Math.max(0.0, modified);
    }
}
