package com.orevault.orevault.skill;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Immutable description of a single skill-tree node.
 *
 * <p>Instances are created once by {@link NodeDefs} and are never mutated.
 * {@code costs} and {@code levelReqs} are parallel arrays where index 0 is tier 1;
 * {@link #maxTier()} is therefore the array length.</p>
 *
 * @param id            stable snake_case identifier (e.g. {@code vein_expansion}). Persisted in
 *                      player save data, so changing one is a migration, not a rename (§6.1)
 * @param name          display name (e.g. {@code Vein Expansion})
 * @param tree          which tree the node belongs to
 * @param cluster       which cluster it sits in; the cluster's anchor gates it (§6.1)
 * @param nodeClass     what kind of node it is (§6 notation)
 * @param forkGroup     fork name shared by a parent and its options, or {@code null}
 * @param forkParentId  the fork parent this option specializes, or {@code null}
 * @param costs         skill-point cost per tier
 * @param levelReqs     minimum team level per tier
 * @param prereqs       nodes that must be unlocked (to a minimum tier) first
 * @param exclusiveWith node id that cannot be held simultaneously, or {@code null}
 * @param ultimineOnly  whether the node only appears when FTB Ultimine is loaded
 */
public record NodeDef(
        String id,
        String name,
        Tree tree,
        Cluster cluster,
        NodeClass nodeClass,
        @Nullable String forkGroup,
        @Nullable String forkParentId,
        int[] costs,
        int[] levelReqs,
        List<Prereq> prereqs,
        @Nullable String exclusiveWith,
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
        // The fork invariants are checked here rather than in a test because a malformed fork is
        // not a balance mistake that shows up in play — it is a node that silently costs points and
        // does nothing, which is the exact failure the fork rework existed to remove.
        switch (nodeClass) {
            case FORK_PARENT -> {
                if (forkGroup == null) {
                    throw new IllegalArgumentException("fork parent needs a forkGroup: " + id);
                }
                if (forkParentId != null) {
                    throw new IllegalArgumentException("fork parent cannot have a parent: " + id);
                }
            }
            case FORK_OPTION -> {
                if (forkGroup == null || forkParentId == null) {
                    throw new IllegalArgumentException("fork option needs group and parent: " + id);
                }
                if (costs.length != 1 || costs[0] != 0) {
                    throw new IllegalArgumentException("fork options cost 0 points: " + id);
                }
            }
            default -> {
                if (forkGroup != null || forkParentId != null) {
                    throw new IllegalArgumentException("only fork nodes carry fork fields: " + id);
                }
            }
        }
        costs = costs.clone();
        levelReqs = levelReqs.clone();
        prereqs = List.copyOf(prereqs);
    }

    /** Highest purchasable tier. */
    public int maxTier() {
        return costs.length;
    }

    /** Whether this node is a free on/off toggle (§6.1). */
    public boolean tradeoff() {
        return nodeClass == NodeClass.TRADEOFF;
    }

    public boolean isExclusive() {
        return exclusiveWith != null;
    }

    /** Whether the node is hidden without FTB Ultimine, either in its own right or by cluster. */
    public boolean hiddenWithoutUltimine() {
        return ultimineOnly || cluster.ultimineOnly();
    }
}
