package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.SkillTree.UnlockResult;

/**
 * The two rules added with the cluster/fork rework (§6.1): a cluster is gated on points
 * spent in the tree, and a fork is one paid parent plus free, mutually exclusive options.
 *
 * <p>Both are the kind of rule a client screen will also implement for display, so both are
 * tested here against the server-authoritative copy — the screen being wrong is a cosmetic
 * bug, this being wrong is a player buying something they have not earned.</p>
 */
class SkillTreeUnlockRulesTest {

    /** High enough that no per-node level requirement is ever the thing under test. */
    private static final int MAX_LEVEL = NodeCosts.LEVEL_CAP;

    private static final int PLENTY_OF_POINTS = 1_000;

    private SkillTree tree() {
        return new SkillTree(Tree.RESONANCE);
    }

    /** Buys every tier of a node, asserting each one succeeds. */
    private void buyAll(SkillTree tree, String nodeId) {
        NodeDef def = NodeDefs.get(nodeId);
        for (int i = 0; i < def.maxTier(); i++) {
            assertEquals(UnlockResult.OK, tree.unlock(nodeId, MAX_LEVEL, PLENTY_OF_POINTS),
                    "expected to buy tier " + (i + 1) + " of " + nodeId);
        }
    }

    /** Spends at least {@code target} points inside Prospecting, which is never gated. */
    private void spendInProspecting(SkillTree tree, int target) {
        buyAll(tree, "vein_expansion");
        buyAll(tree, "stone_memory");
        buyAll(tree, "miners_constitution");
        buyAll(tree, "gravel_purge");
        assertTrue(tree.skillPointsInvested() >= target,
                "Prospecting cannot fund a " + target + "-point gate; the fixture needs more nodes");
    }

    // ----- anchor gates -----

    @Test
    void prospectingIsOpenFromTheFirstPoint() {
        assertEquals(0, NodeDefs.anchorGate(Cluster.PROSPECTING));
        assertEquals(UnlockResult.OK, tree().canUnlock("vein_expansion", MAX_LEVEL, PLENTY_OF_POINTS));
    }

    @Test
    void aGatedClusterIsClosedUntilEnoughPointsAreSpentAnywhere() {
        SkillTree tree = tree();
        assertTrue(NodeDefs.anchorGate(Cluster.EXCAVATION) > 0);

        // vein_proliferation's own prerequisite is met, and level and points are not the issue.
        assertEquals(UnlockResult.OK, tree.unlock("vein_expansion", MAX_LEVEL, PLENTY_OF_POINTS));
        assertEquals(UnlockResult.ANCHOR_LOCKED,
                tree.canUnlock("vein_proliferation", MAX_LEVEL, PLENTY_OF_POINTS));
    }

    @Test
    void aGatedClusterOpensOncePointsAreSpent() {
        SkillTree tree = tree();
        spendInProspecting(tree, NodeDefs.anchorGate(Cluster.EXCAVATION));

        assertEquals(UnlockResult.OK, tree.canUnlock("vein_proliferation", MAX_LEVEL, PLENTY_OF_POINTS));
    }

    @Test
    void refundingBackBelowAGateReClosesTheCluster() {
        SkillTree tree = tree();
        spendInProspecting(tree, NodeDefs.anchorGate(Cluster.EXCAVATION));
        assertEquals(UnlockResult.OK, tree.unlock("vein_proliferation", MAX_LEVEL, PLENTY_OF_POINTS));

        while (tree.skillPointsInvested() >= NodeDefs.anchorGate(Cluster.EXCAVATION)) {
            assertTrue(tree.refund("stone_memory", true) >= 0
                            || tree.refund("miners_constitution", true) >= 0
                            || tree.refund("vein_expansion", true) >= 0,
                    "ran out of Prospecting tiers to refund before dropping below the gate");
        }

        // The tier already bought keeps working; only further purchases are blocked (§6.1).
        assertEquals(1, tree.unlockedTier("vein_proliferation"));
        assertEquals(UnlockResult.ANCHOR_LOCKED,
                tree.canUnlock("vein_proliferation", MAX_LEVEL, PLENTY_OF_POINTS));
    }

    // ----- forks -----

    @Test
    void aForkOptionCostsNothing() {
        assertEquals(0, NodeDefs.get("abundance").costs()[0]);
        assertEquals(NodeClass.FORK_OPTION, NodeDefs.get("abundance").nodeClass());
    }

    @Test
    void aForkOptionCannotBeTakenBeforeItsParent() {
        SkillTree tree = tree();
        spendInProspecting(tree, NodeDefs.anchorGate(Cluster.EXCAVATION));

        assertEquals(UnlockResult.PREREQ_MISSING, tree.canUnlock("abundance", MAX_LEVEL, 0));
    }

    @Test
    void aForkOptionIsFreeOncePaidParentIsHeld() {
        SkillTree tree = forkReadyTree();

        // Zero available points on purpose: a free option must not be blocked by an empty pool.
        assertEquals(UnlockResult.OK, tree.unlock("abundance", MAX_LEVEL, 0));
        assertEquals(1, tree.unlockedTier("abundance"));
    }

    @Test
    void onlyOneOptionPerForkMayBeHeld() {
        SkillTree tree = forkReadyTree();
        assertEquals(UnlockResult.OK, tree.unlock("abundance", MAX_LEVEL, 0));

        assertEquals(UnlockResult.FORK_OPTION_TAKEN, tree.canUnlock("stratified", MAX_LEVEL, 0));
        assertEquals(UnlockResult.FORK_OPTION_TAKEN, tree.canUnlock("vein_singularity", MAX_LEVEL, 0));
    }

    @Test
    void unpickingAnOptionIsFreeAndReleasesItsSiblings() {
        SkillTree tree = forkReadyTree();
        assertEquals(UnlockResult.OK, tree.unlock("abundance", MAX_LEVEL, 0));

        assertEquals(0, tree.refundCost("abundance", false), "a node that cost nothing refunds for nothing");
        assertEquals(0, tree.refund("abundance", false));
        assertEquals(0, tree.unlockedTier("abundance"));
        assertEquals(UnlockResult.OK, tree.canUnlock("stratified", MAX_LEVEL, 0));
    }

    @Test
    void refundingAForkParentToZeroClearsTheChosenOption() {
        SkillTree tree = forkReadyTree();
        assertEquals(UnlockResult.OK, tree.unlock("abundance", MAX_LEVEL, 0));

        while (tree.isUnlocked("vein_shaping")) {
            assertTrue(tree.refund("vein_shaping", true) >= 0);
        }

        assertEquals(0, tree.unlockedTier("vein_shaping"));
        assertEquals(0, tree.unlockedTier("abundance"),
                "an option surviving its parent is a free effect nobody paid for");
    }

    @Test
    void aPaidNodeStillCostsPointsToRefund() {
        SkillTree tree = tree();
        assertEquals(UnlockResult.OK, tree.unlock("vein_expansion", MAX_LEVEL, PLENTY_OF_POINTS));
        assertNotEquals(0, tree.refundCost("vein_expansion", false));
    }

    /** A tree with Excavation open and Vein Shaping bought, ready for a Vein Shape option. */
    private SkillTree forkReadyTree() {
        SkillTree tree = tree();
        spendInProspecting(tree, NodeDefs.anchorGate(Cluster.EXCAVATION));
        buyAll(tree, "vein_proliferation");
        assertEquals(UnlockResult.OK, tree.unlock("vein_shaping", MAX_LEVEL, PLENTY_OF_POINTS));
        return tree;
    }
}
