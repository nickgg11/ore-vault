package com.orevault.orevault.core.mining;

/**
 * What the Consume, Fortune and Quantity stages contributed to one break, applied once by the pipeline rather than by each
 * node (§11 Drop Pipeline). Because the pipeline applies it, Quantity multipliers compose multiplicatively and the loot
 * table is re-rolled exactly once however many nodes raise Fortune. Applying Fortune and Quantity to the drops (the
 * re-roll, the {@code ItemEntity} re-wrapping) and refusing a nonsense contribution land with the first Fortune node,
 * E9-S26 (#330); until then no node contributes.
 */
public final class DropOutcome {
    private boolean consumed;
    private int fortune;
    private double quantity = 1.0;

    /** The block drops no items. Its vanilla XP still drops (§3.4). */
    public void consume() {
        consumed = true;
    }

    public void addFortune(int levels) {
        fortune += levels;
    }

    public void multiplyQuantity(double factor) {
        quantity *= factor;
    }

    public boolean consumed() {
        return consumed;
    }

    public int fortune() {
        return fortune;
    }

    public double quantity() {
        return quantity;
    }
}
