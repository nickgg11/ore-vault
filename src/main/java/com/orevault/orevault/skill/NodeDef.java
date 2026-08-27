package com.orevault.orevault.skill;

import java.util.List;

/**
 * Immutable description of a single skill-tree node.
 *
 * <p>Instances are created once by {@link NodeDefs} and are never mutated.
 * {@code costs} and {@code levelReqs} are parallel arrays where index 0 is tier 1;
 * {@link #maxTier()} is therefore the array length.</p>
 *
 * @param id            stable snake_case identifier (e.g. {@code vein_expansion})
 * @param name          display name (e.g. {@code Vein Expansion})
 * @param tree          which tree the node belongs to
 * @param branch        branch label used for UI grouping
 * @param costs         skill-point cost per tier
 * @param levelReqs     minimum team level per tier
 * @param prereqs       nodes that must be unlocked (to a minimum tier) first
 * @param tradeoff      whether the node is a free on/off toggle
 * @param exclusiveWith node id that cannot be active simultaneously, or {@code null}
 * @param ultimineOnly  whether the node only appears when FTB Ultimine is loaded
 */
public record NodeDef(
        String id,
        String name,
        Tree tree,
        String branch,
        int[] costs,
        int[] levelReqs,
        List<Prereq> prereqs,
        boolean tradeoff,
        String exclusiveWith,
        boolean ultimineOnly) {

    public enum Tree {
        RESONANCE,
        ANIMUS
    }

    /** A prerequisite: {@code nodeId} must be unlocked to at least {@code minTier}. */
    public record Prereq(String nodeId, int minTier) {
        public Prereq {
            if (minTier < 1) {
                throw new IllegalArgumentException("minTier must be >= 1 for prereq on " + nodeId);
            }
        }
    }

    public NodeDef {
        if (costs.length == 0) {
            throw new IllegalArgumentException("costs must be non-empty: " + id);
        }
        if (costs.length != levelReqs.length) {
            throw new IllegalArgumentException("costs/levelReqs length mismatch: " + id);
        }
        costs = costs.clone();
        levelReqs = levelReqs.clone();
        prereqs = List.copyOf(prereqs);
    }

    /** Highest purchasable tier. */
    public int maxTier() {
        return costs.length;
    }

    public boolean isExclusive() {
        return exclusiveWith != null;
    }
}
