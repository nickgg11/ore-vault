package com.orevault.orevault.skill;

/**
 * What kind of node this is (§6 notation table).
 *
 * <p>This replaces the three independent booleans the record used to carry
 * ({@code tradeoff}, and the implied "is it exclusive", "is it a fork"). Those were added one at a
 * time as the classes appeared, and by the time the tree had eight of them the combinations that
 * were meaningless — a tradeoff fork option, an exclusive anchor — outnumbered the ones that were
 * not. A single closed enum makes the illegal states unrepresentable and gives the Tome one field
 * to switch on when it picks a frame (§8).</p>
 *
 * <p>{@code EXCLUSIVE} is deliberately <em>not</em> here. Exclusivity is a relationship between two
 * nodes, not a kind of node: Vault's Blessing is an ordinary small node that happens to lock out its
 * partner, and Greedy Seams is a {@link #PACT} that does the same. It stays a separate field.</p>
 */
public enum NodeClass {

    /** A tiered percentage bonus with no downside. The filler you path through. */
    SMALL,

    /** A single-tier node granting a distinct mechanic rather than a bigger number. Pure upside. */
    NOTABLE,

    /**
     * Build-defining, expensive, always carries a real downside, and only ever found in
     * {@link Cluster#MASTERY} — a rule {@code NodeDefsConsistencyTest} enforces rather than trusts.
     */
    KEYSTONE,

    /**
     * A keystone bought early. Same weight and same real downside, but reachable from an ordinary
     * cluster instead of from behind the 100-point Mastery gate.
     *
     * <p>Pacts exist because three of the tree's sharpest choices are only interesting while they
     * are cheap: Greedy Seams undercutting Ore Doubling on raw yield is a decision at 4 points and
     * nothing at all at 100. Rather than reprice them into irrelevance or break the rule that
     * keystones sit at the bottom of the tree, they became their own class.</p>
     */
    PACT,

    /**
     * Strong immediately and worthless later <em>by construction</em> — a flat cap or a hard level
     * cutoff, so rising throughput retires it rather than a designer's judgement.
     *
     * <p>The point stays spent. Refunding on retirement would make every growth node a free pick,
     * which is the opposite of the decision they are meant to pose; the Tome greys the node and
     * labels it Outgrown instead (§8).</p>
     */
    GROWTH,

    /** Toggleable on and off at no cost, but only while outside the Vault. */
    TRADEOFF,

    /** A tiered node that costs points and does nothing until one of its options is chosen. */
    FORK_PARENT,

    /** Costs 0 points; decides what its parent's tiers do. Exactly one option per fork at a time. */
    FORK_OPTION;

    /** Whether a node of this class can ever be bought with skill points. */
    public boolean purchasable() {
        return this != FORK_OPTION;
    }
}
