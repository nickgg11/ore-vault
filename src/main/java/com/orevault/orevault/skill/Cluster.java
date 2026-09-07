package com.orevault.orevault.skill;

/**
 * A named stage of the miner's craft, and the unit the Resonance tree is built from (§6.1).
 *
 * <p>Each cluster is headed by an <b>anchor</b>: not a node, and deliberately not in
 * {@link NodeDefs}. An anchor is not purchasable, costs nothing, and holds exactly one piece of
 * state — the number of skill points that must be spent <em>anywhere in the tree</em> before the
 * cluster opens. Modelling it as a zero-cost node would mean every unlock path had to special-case
 * "you cannot buy this", so it lives here instead, as the property of the cluster it names.</p>
 *
 * <h2>Gates are fractions, not point totals</h2>
 *
 * <p>The spec states the gates in points (0 / 10 / 25 / 45 / 55 / 70 / 100), but those numbers were
 * derived from a tree whose total cost has since moved and will move again — every node added
 * pushes them out of proportion. The fraction of a full build is the thing that is actually being
 * specified, so that is what is stored, and {@link NodeDefs#anchorGate(Cluster)} multiplies it by
 * the tree's real total. The two cannot drift.</p>
 *
 * <p>Ordinal order is the order clusters are drawn down the tree, with one exception:
 * {@link #BROAD_CUT} shares Assay's gate and is absent entirely without FTB Ultimine, so it hangs
 * off the side rather than sitting in the run.</p>
 */
public enum Cluster {

    /** Reading the rock, and surviving long enough to keep reading it. Open from the first point. */
    PROSPECTING("Prospecting", 0.00, false),

    /** Moving rock in bulk, and deciding what shape it comes in. */
    EXCAVATION("Excavation", 0.04, false),

    /** Telling one ore from another, and knowing what the stone remembers. */
    ASSAY("Assay", 0.11, false),

    /** Wide-swing mining. Absent entirely without FTB Ultimine. */
    BROAD_CUT("Broad Cut", 0.11, true),

    /** Getting more out of each ore than the ore contains. */
    METALLURGY("Metallurgy", 0.20, false),

    /** The ground is yours. Holding it, reaching into it, being kept by it. */
    CLAIM("Claim", 0.25, false),

    /** The Vault answering back, and what it gives you for finishing what you started. */
    DEEP_LORE("Deep Lore", 0.31, false),

    /** Keystones only, at the bottom of the tree. */
    MASTERY("Mastery", 0.45, false),

    /** Animus: Disturbed Zone enhancement (§6.2). Ungated — the Animus tree has no anchors yet. */
    ANIMUS_ZONES("Disturbed Zone Enhancement", 0.00, false),

    /** Animus: mob rewards (§6.2). Ungated. */
    ANIMUS_REWARDS("Mob Rewards", 0.00, false);

    private final String displayName;
    private final double gateFraction;
    private final boolean ultimineOnly;

    Cluster(String displayName, double gateFraction, boolean ultimineOnly) {
        this.displayName = displayName;
        this.gateFraction = gateFraction;
        this.ultimineOnly = ultimineOnly;
    }

    public String displayName() {
        return displayName;
    }

    /** Fraction of the tree's total cost that must be spent before this cluster's anchor opens. */
    public double gateFraction() {
        return gateFraction;
    }

    /** Whether the whole cluster is hidden when FTB Ultimine is absent. */
    public boolean ultimineOnly() {
        return ultimineOnly;
    }
}
