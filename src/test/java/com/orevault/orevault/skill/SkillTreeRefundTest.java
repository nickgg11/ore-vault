package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.skill.NodeDef.Tree;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for the §4.4 refund price and the post-reset free window. */
class SkillTreeRefundTest {

    /** A 1-point tier-1 node with no prerequisites. */
    private static final String ONE_POINT_NODE = "gravel_purge";
    /** The 10-point Vault Expansion keystone. */
    private static final String TEN_POINT_NODE = "vault_expansion";

    /** Buys {@code tiers} tiers of a node, bypassing level and point gates. */
    private static SkillTree treeWith(String nodeId, int tiers) {
        SkillTree tree = new SkillTree(Tree.RESONANCE);
        tree.setUnlockedTier(nodeId, tiers);
        return tree;
    }

    @Test
    void refundCostsThreeXpLevelsPerSkillPoint() {
        assertEquals(3, treeWith(ONE_POINT_NODE, 1).refund(ONE_POINT_NODE, false));
        assertEquals(30, treeWith(TEN_POINT_NODE, 1).refund(TEN_POINT_NODE, false));
    }

    @Test
    void refundIsFreeInsideTheWindow() {
        assertEquals(0, treeWith(ONE_POINT_NODE, 1).refund(ONE_POINT_NODE, true));
        assertEquals(0, treeWith(TEN_POINT_NODE, 1).refund(TEN_POINT_NODE, true));
    }

    @Test
    void refundPricesTheTierBeingRemovedNotTheWholeNode() {
        // vein_expansion tier costs are {1, 1, 2, 2, 3}; refunding from tier 5 undoes the 3-point tier.
        SkillTree tree = treeWith("vein_expansion", 5);
        assertEquals(3 * NodeCosts.VEIN_EXPANSION_COSTS[4], tree.refund("vein_expansion", false));
        assertEquals(4, tree.unlockedTier("vein_expansion"));
        assertEquals(3 * NodeCosts.VEIN_EXPANSION_COSTS[3], tree.refund("vein_expansion", false));
    }

    @Test
    void priceDoesNotDependOnHowInvestedTheTreeIs() {
        // The old formula scaled with total tree investment, so the same node cost
        // wildly different amounts depending on unrelated purchases.
        SkillTree sparse = treeWith(ONE_POINT_NODE, 1);
        SkillTree loaded = treeWith(ONE_POINT_NODE, 1);
        loaded.setUnlockedTier("ore_doubling", 6);
        loaded.setUnlockedTier(TEN_POINT_NODE, 1);
        assertTrue(loaded.skillPointsInvested() > sparse.skillPointsInvested());
        assertEquals(sparse.refundCost(ONE_POINT_NODE, false), loaded.refundCost(ONE_POINT_NODE, false));
    }

    @Test
    void refundRemovesTheTierAndClearsTheNodeAtTierOne() {
        SkillTree tree = treeWith(ONE_POINT_NODE, 1);
        tree.refund(ONE_POINT_NODE, false);
        assertEquals(0, tree.unlockedTier(ONE_POINT_NODE));
        assertTrue(!tree.isUnlocked(ONE_POINT_NODE));
    }

    @Test
    void refundingAnUnpurchasedOrUnknownNodeReportsFailure() {
        SkillTree tree = new SkillTree(Tree.RESONANCE);
        assertEquals(-1, tree.refund(ONE_POINT_NODE, false));
        assertEquals(-1, tree.refund("no_such_node", false));
        assertEquals(-1, tree.refundCost(ONE_POINT_NODE, true), "free window must not mask a failed refund");
    }

    @Test
    void refundCostDoesNotMutateTheTree() {
        SkillTree tree = treeWith(TEN_POINT_NODE, 1);
        assertEquals(30, tree.refundCost(TEN_POINT_NODE, false));
        assertEquals(1, tree.unlockedTier(TEN_POINT_NODE));
    }

    @Test
    void fullResonanceRespecIsExpensiveButNotImpossible() {
        int fullTree = NodeDefs.totalTreeCost(Tree.RESONANCE);
        int totalXp = NodeCosts.REFUND_XP_PER_POINT * fullTree;
        assertEquals(3 * fullTree, totalXp);
        // The old formula put a late respec near 3,250 levels — effectively a trap.
        assertTrue(totalXp < 1000, "full respec costs " + totalXp + " XP levels");
    }
}
