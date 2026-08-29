package com.orevault.orevault.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;

/**
 * Team-shared skill tree state: which nodes (and tiers) have been purchased,
 * plus pure validation for unlocking, refunding, and exclusive-pair enforcement.
 *
 * <p>Skill points and team level are supplied by the caller (they live in the
 * team's SavedData); this class only tracks unlocked tiers and enforces rules.</p>
 *
 * <p>Tradeoff nodes are purchased here like any other node. Their per-player
 * on/off toggle state is deliberately NOT stored here (§6.1: "toggle state is
 * saved per-player"); {@link #toggleTradeoff(Set, String)} operates on a
 * caller-owned set so each player keeps their own active set.</p>
 */
public final class SkillTree {

    public enum UnlockResult {
        OK,
        UNKNOWN_NODE,
        WRONG_TREE,
        MAX_TIER,
        LEVEL_TOO_LOW,
        NOT_ENOUGH_POINTS,
        PREREQ_MISSING,
        EXCLUSIVE_CONFLICT
    }

    private final Tree tree;
    private final Map<String, Integer> unlockedTiers = new HashMap<>();

    public SkillTree(Tree tree) {
        this.tree = tree;
    }

    public Tree tree() {
        return tree;
    }

    /** Unlocked tier of {@code nodeId} (0 = not unlocked). */
    public int unlockedTier(String nodeId) {
        return unlockedTiers.getOrDefault(nodeId, 0);
    }

    /** Snapshot of all unlocked node tiers (node id → tier). */
    public Map<String, Integer> getUnlockedTiers() {
        return Map.copyOf(unlockedTiers);
    }

    /** Directly sets an unlocked tier; used only when loading persisted data. */
    public void setUnlockedTier(String nodeId, int tier) {
        unlockedTiers.put(nodeId, tier);
    }

    public boolean isUnlocked(String nodeId) {
        return unlockedTier(nodeId) > 0;
    }

    /** Total skill points invested in this tree across all unlocked tiers. */
    public int skillPointsInvested() {
        int total = 0;
        for (Map.Entry<String, Integer> entry : unlockedTiers.entrySet()) {
            NodeDef def = NodeDefs.get(entry.getKey());
            int tier = entry.getValue();
            for (int i = 0; i < tier; i++) {
                total += def.costs()[i];
            }
        }
        return total;
    }

    /** Skill-point cost of the next tier of {@code nodeId}, or 0 if unknown or already maxed. */
    public int nextTierCost(String nodeId) {
        NodeDef def = NodeDefs.get(nodeId);
        if (def == null) {
            return 0;
        }
        int current = unlockedTier(nodeId);
        if (current >= def.maxTier()) {
            return 0;
        }
        return def.costs()[current];
    }

    /** Validates whether the next tier of {@code nodeId} can be purchased. */
    public UnlockResult canUnlock(String nodeId, int teamLevel, int availableSkillPoints) {
        NodeDef def = NodeDefs.get(nodeId);
        if (def == null) {
            return UnlockResult.UNKNOWN_NODE;
        }
        if (def.tree() != tree) {
            return UnlockResult.WRONG_TREE;
        }
        int current = unlockedTier(nodeId);
        if (current >= def.maxTier()) {
            return UnlockResult.MAX_TIER;
        }
        int nextTier = current + 1;
        if (teamLevel < def.levelReqs()[nextTier - 1]) {
            return UnlockResult.LEVEL_TOO_LOW;
        }
        if (availableSkillPoints < def.costs()[nextTier - 1]) {
            return UnlockResult.NOT_ENOUGH_POINTS;
        }
        for (Prereq prereq : def.prereqs()) {
            if (unlockedTier(prereq.nodeId()) < prereq.minTier()) {
                return UnlockResult.PREREQ_MISSING;
            }
        }
        if (def.isExclusive()) {
            NodeDef conflict = NodeDefs.get(def.exclusiveWith());
            if (conflict != null && isUnlocked(conflict.id())) {
                return UnlockResult.EXCLUSIVE_CONFLICT;
            }
        }
        return UnlockResult.OK;
    }

    /** Purchases the next tier if valid; the caller deducts the skill-point cost. */
    public UnlockResult unlock(String nodeId, int teamLevel, int availableSkillPoints) {
        UnlockResult result = canUnlock(nodeId, teamLevel, availableSkillPoints);
        if (result != UnlockResult.OK) {
            return result;
        }
        unlockedTiers.put(nodeId, unlockedTier(nodeId) + 1);
        return UnlockResult.OK;
    }

    /**
     * XP-level cost of refunding the highest unlocked tier of {@code nodeId},
     * without performing the refund.
     *
     * @param freeRespec {@code true} while the post-reset free window is open (§3.5)
     * @return the cost per §4.4, or -1 if the node has no tier to refund
     */
    public int refundCost(String nodeId, boolean freeRespec) {
        int current = unlockedTier(nodeId);
        NodeDef def = NodeDefs.get(nodeId);
        // current can exceed maxTier if a node's tier list shrank between versions:
        // setUnlockedTier loads persisted tiers verbatim, so never index costs() blind.
        if (def == null || current <= 0 || current > def.maxTier()) {
            return -1;
        }
        return freeRespec ? 0 : NodeCosts.REFUND_XP_PER_POINT * def.costs()[current - 1];
    }

    /**
     * Refunds the highest unlocked tier of {@code nodeId}.
     *
     * <p>The price is {@code 3 XP levels × that tier's skill-point cost} (§4.4),
     * so it is always proportional to what is being undone — a 1-point node
     * costs 3, the 10-point Vault Expansion keystone costs 30. The previous
     * formula scaled with total tree investment instead, pricing every node
     * identically: an early mistake was nearly free and a late respec cost
     * ~3,250 levels, which would have made every one-way fork in §6.1 a trap.</p>
     *
     * @param freeRespec {@code true} while the post-reset free window is open (§3.5)
     * @return the XP-level cost, or -1 if the node has no tier to refund
     */
    public int refund(String nodeId, boolean freeRespec) {
        int xpCost = refundCost(nodeId, freeRespec);
        if (xpCost < 0) {
            return -1;
        }
        int current = unlockedTier(nodeId);
        if (current == 1) {
            unlockedTiers.remove(nodeId);
        } else {
            unlockedTiers.put(nodeId, current - 1);
        }
        return xpCost;
    }

    /**
     * Toggles a purchased tradeoff node on/off in the given (per-player) active set.
     *
     * @param activeTradeoffs caller-owned set of currently active tradeoff node ids
     * @return {@code true} if the node is now active, {@code false} if now inactive
     *         (or the node is not yet purchased).
     * @throws IllegalArgumentException if {@code nodeId} is not a tradeoff node
     */
    public boolean toggleTradeoff(Set<String> activeTradeoffs, String nodeId) {
        NodeDef def = NodeDefs.get(nodeId);
        if (def == null || !def.tradeoff()) {
            throw new IllegalArgumentException("Not a tradeoff node: " + nodeId);
        }
        if (!isUnlocked(nodeId)) {
            return false;
        }
        if (activeTradeoffs.contains(nodeId)) {
            activeTradeoffs.remove(nodeId);
            return false;
        }
        activeTradeoffs.add(nodeId);
        return true;
    }
}
