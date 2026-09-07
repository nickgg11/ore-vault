package com.orevault.orevault.skill;

/**
 * The arithmetic behind the §6.1 nodes whose effect is a curve rather than a flag.
 *
 * <p>Breakpoints, growth nodes and the depth keystones all turn some tracked number — lifetime
 * blocks broken, the deepest Y ever reached, veins completed in a row — into a bonus. That
 * conversion is ordinary logic with a right answer, and it is the part most likely to be wrong at
 * an end: a lifetime counter that never stops growing, a depth bonus that pays nothing at the only
 * depth it applies to, a streak that punishes a mis-swing harder than a whole session of care.</p>
 *
 * <p>So it lives here, where {@code src/test/java} can reach it. Nothing in this class may import
 * a Minecraft type; the gameplay code reads the stat, calls a method, and applies the result.</p>
 *
 * <p>Every method takes the node's <b>unlocked tier</b> and returns zero — no bonus, no effect — at
 * tier 0. Callers do not need to check whether the node is bought.</p>
 */
public final class NodeEffects {

    private NodeEffects() {
    }

    // =====================================================================
    // Calloused Hands (§6.1, Assay)
    // =====================================================================

    /**
     * Fractional Resonance bonus from lifetime blocks broken.
     *
     * <p>{@code K x ln(1 + blocks / 10,000)}, capped. The logarithm is what makes this safe to
     * leave running forever: doubling the block count adds a constant, not a multiple. The linear
     * form it replaced ({@code blocks x 0.0001} read as a multiplier) reached x30 over a normal
     * 100-hour progression and kept climbing.</p>
     *
     * @param tier           unlocked tier of Calloused Hands, 0 for unbought
     * @param lifetimeBlocks {@code PlayerStats#totalBlocksBroken}
     */
    public static double callousedHandsBonus(int tier, long lifetimeBlocks) {
        if (tier <= 0 || lifetimeBlocks <= 0) {
            return 0.0;
        }
        double coefficient = NodeCosts.CALLOUSED_HANDS_COEFFICIENTS[
                Math.min(tier, NodeCosts.CALLOUSED_HANDS_COEFFICIENTS.length) - 1];
        double bonus = coefficient * Math.log(1.0 + lifetimeBlocks / NodeCosts.CALLOUSED_HANDS_SCALE);
        return Math.min(bonus, NodeCosts.CALLOUSED_HANDS_CAP);
    }

    // =====================================================================
    // Highwater Mark (§6.1, Metallurgy)
    // =====================================================================

    /**
     * Durability repaired on the held tool per ore mined.
     *
     * <p>One, plus one more per 16 blocks the all-time depth record sits below Y=64. Rounded
     * <em>up</em>, so the stated maximum is actually reachable: the expanded Vault's lowest
     * breakable block is Y=-63, which is 127 blocks below the baseline and would otherwise fall
     * one step short of the 9 the spec promises.</p>
     *
     * @param tier     unlocked tier of Highwater Mark, 0 for unbought
     * @param deepestY {@code PlayerStats#deepestY}, a lifetime record that never rises
     */
    public static int highwaterMarkRepair(int tier, int deepestY) {
        if (tier <= 0) {
            return 0;
        }
        int below = Math.max(0, NodeCosts.HIGHWATER_MARK_BASELINE_Y - deepestY);
        int steps = Math.ceilDiv(below, NodeCosts.HIGHWATER_MARK_BLOCKS_PER_POINT);
        return Math.min(1 + steps, NodeCosts.HIGHWATER_MARK_MAX_REPAIR);
    }

    /** Whether the repair also spills onto worn armour when the held tool needs none. */
    public static boolean highwaterMarkRepairsArmour(int tier) {
        return tier >= 2;
    }

    // =====================================================================
    // Vein Discipline (§6.1, Deep Lore)
    // =====================================================================

    /** Fractional Resonance bonus for a completion streak of {@code streak} veins. */
    public static double veinDisciplineBonus(int streak) {
        int effective = Math.min(Math.max(streak, 0), NodeCosts.VEIN_DISCIPLINE_MAX_STREAK);
        return effective * NodeCosts.VEIN_DISCIPLINE_PER_VEIN;
    }

    /**
     * The streak after abandoning a part-mined vein.
     *
     * <p>Down three, floored at zero — not back to zero. A hard reset means one stray swing at the
     * edge of a vein you had not noticed costs fifteen veins of work, and the rational response to
     * that is to stop exploring and mine defensively, which is the opposite of what the completion
     * group is for.</p>
     */
    public static int veinDisciplineAfterAbandon(int streak) {
        return Math.max(0, streak - NodeCosts.VEIN_DISCIPLINE_ABANDON_PENALTY);
    }

    // =====================================================================
    // Growth nodes (§6.1, Prospecting)
    // =====================================================================

    /**
     * Novice's Luck: {@code (30 - teamLevel)%} bonus ore, floored at zero.
     *
     * <p>Retires itself against the level cap rather than against a designer's judgement, which is
     * what makes it a growth node: there is no level at which it is quietly still worth something.</p>
     */
    public static double novicesLuckBonus(int teamLevel) {
        return Math.max(0, NodeCosts.NOVICES_LUCK_ZERO_AT_LEVEL - teamLevel) / 100.0;
    }

    /** Whether Shallow Grace still applies, given the player's all-time depth record. */
    public static boolean shallowGraceApplies(int deepestY) {
        return deepestY > NodeCosts.SHALLOW_GRACE_MAX_DEPTH_Y;
    }

    /** Whether a growth node gated on team level has been outgrown (§8: shown as "Outgrown"). */
    public static boolean outgrownAtLevel(int teamLevel, int retireLevel) {
        return teamLevel >= retireLevel;
    }

    // =====================================================================
    // Within-trip breakpoints (§6.1, Excavation)
    // =====================================================================

    /** Deep Habit: +5% per 1,000 blocks broken this trip, to +25%. Resets on leaving. */
    public static double deepHabitBonus(long blocksThisTrip) {
        if (blocksThisTrip <= 0) {
            return 0.0;
        }
        long steps = blocksThisTrip / NodeCosts.DEEP_HABIT_BLOCKS_PER_STEP;
        return Math.min(steps * NodeCosts.DEEP_HABIT_STEP, NodeCosts.DEEP_HABIT_CAP);
    }

    /**
     * Long Delve stacks earned by {@code minutesThisTrip} unbroken minutes inside the Vault.
     *
     * <p>The caller is responsible for the AFK guard: minutes only count while a block has been
     * broken within {@link NodeCosts#LONG_DELVE_IDLE_SECONDS}. Without it this node pays players
     * for standing still, which is the opposite of what it is for.</p>
     */
    public static int longDelveStacks(int tier, long minutesThisTrip) {
        if (tier <= 0 || minutesThisTrip < 0) {
            return 0;
        }
        int cap = NodeCosts.LONG_DELVE_STACK_CAPS[
                Math.min(tier, NodeCosts.LONG_DELVE_STACK_CAPS.length) - 1];
        return (int) Math.min(minutesThisTrip / NodeCosts.LONG_DELVE_MINUTES_PER_STACK, cap);
    }

    /** Long Delve: mining speed bonus from the stacks currently held. */
    public static double longDelveMiningSpeedBonus(int stacks) {
        return Math.max(0, stacks) * NodeCosts.LONG_DELVE_MINING_SPEED_PER_STACK;
    }

    // =====================================================================
    // Stonecutter's Patience (§6.1, Assay)
    // =====================================================================

    /** Bonus vanilla XP per ore, from lifetime natural stone broken, capped per tier. */
    public static int stonecuttersPatienceXp(int tier, long stoneBroken) {
        if (tier <= 0 || stoneBroken <= 0) {
            return 0;
        }
        int max = NodeCosts.STONECUTTERS_PATIENCE_MAX_XP[
                Math.min(tier, NodeCosts.STONECUTTERS_PATIENCE_MAX_XP.length) - 1];
        long earned = stoneBroken / NodeCosts.STONECUTTERS_PATIENCE_STONE_PER_XP;
        return (int) Math.min(earned, max);
    }

    // =====================================================================
    // Kindred Rock (§6.1, Metallurgy)
    // =====================================================================

    /** Whether a player has mined enough of one ore type for it to be attuned. */
    public static boolean kindredRockAttuned(int tier, int minedOfThisType) {
        return tier > 0 && minedOfThisType >= NodeCosts.KINDRED_ROCK_THRESHOLD;
    }

    // =====================================================================
    // Bedrock Communion (§6.1, Mastery)
    // =====================================================================

    /**
     * Ore and Resonance multiplier at height {@code y}: +2% per block below Y=0.
     *
     * <p>Reaches x2.26 at Y=-63 and stops there, because that is the lowest breakable block in an
     * expanded Vault. The ceiling is a fact about the layer stack, not a clamp.</p>
     */
    public static double bedrockCommunionYieldMultiplier(int y) {
        if (y >= 0) {
            return 1.0;
        }
        return 1.0 + NodeCosts.BEDROCK_COMMUNION_PER_BLOCK * -y;
    }

    /**
     * Whether height {@code y} sits in Bedrock Communion's penalty band: no Resonance from ore and
     * halved mining speed.
     *
     * <p>The band stops at the base of the dirt band (Y=246). Everything a player builds on or
     * above the surface is above that line, so surface bases and automation floors are untouched —
     * which is the only reason this keystone is playable rather than a tax on building.</p>
     */
    public static boolean bedrockCommunionPenalised(int y) {
        return y >= NodeCosts.BEDROCK_COMMUNION_PENALTY_MIN_Y && y < NodeCosts.VAULT_DIRT_BAND_MIN_Y;
    }

    /**
     * Mining speed multiplier under Bedrock Communion.
     *
     * <p>A speed penalty rather than damage over time: damage in a dimension a player spends hours
     * in is an irritation rather than a cost, and it would stack lethally with Molten Seam.</p>
     */
    public static double bedrockCommunionMiningSpeedFactor(int y) {
        return bedrockCommunionPenalised(y) ? NodeCosts.BEDROCK_COMMUNION_SPEED_PENALTY : 1.0;
    }
}
