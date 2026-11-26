package com.orevault.orevault.core.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * The six stages of the drop pipeline, declared in the order they run (§11 Drop Pipeline). The order changes the result:
 * Fortune re-rolls the loot table, so it runs before Quantity or it would discard what Ore Doubling added; Consume runs
 * first so a consumed block never rolls Fortune; Transform follows Quantity so Smelter's Intuition smelts the doubled
 * output.
 */
public enum DropStage {
    /** Tithe, Brittle Stone, Stone Curse. Clears items only, never XP (§3.4). Contributes to {@link DropOutcome}. */
    CONSUME,
    /** Vein Fortune, Vault's Purity, Ore Imprint. Contributes to {@link DropOutcome}; the re-roll runs once, after it. */
    FORTUNE,
    /** Ore Doubling, Greedy Seams and the other multipliers. Contributes to {@link DropOutcome}, applied once. */
    QUANTITY,
    /** Smelter's Intuition, Molten Seam, Runic Attunement, dust substitution, Kiln Touch. Edits the list in place. */
    TRANSFORM,
    /** Bonus items, bonus vanilla XP and orbs, the base ore orb first. Appends. */
    BONUS,
    /** Budding Seam, Seam Courier, and the Tempered, Ascending Edge and Echoing Edge counters on the live stack. */
    DELIVER;

    /**
     * The stages that run once Consume has. A consumed block has no items left for Fortune, Quantity or Transform to act
     * on, and ancient debris skips stages 2 to 4 and always drops exactly one (§11, Ancient Traces). Bonus and Deliver
     * always run: vanilla XP, orbs and the per-break counters do not depend on what the block dropped.
     *
     * @param consumed a Consume stage consumed the block
     * @param skipsYield the block is ancient debris
     */
    public static List<DropStage> afterConsume(boolean consumed, boolean skipsYield) {
        List<DropStage> stages = new ArrayList<>(List.of(values()).subList(FORTUNE.ordinal(), values().length));
        if (consumed || skipsYield) {
            stages.removeAll(List.of(FORTUNE, QUANTITY, TRANSFORM));
        }
        return List.copyOf(stages);
    }
}
