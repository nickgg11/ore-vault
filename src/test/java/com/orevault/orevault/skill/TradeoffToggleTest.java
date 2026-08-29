package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.skill.NodeDef.Tree;

import org.junit.jupiter.api.Test;

/**
 * Tests the §6.1 rule that tradeoff toggles are a loadout committed to before
 * entering the Vault: free and unlimited outside, rejected inside.
 */
class TradeoffToggleTest {

    private static final String TRADEOFF_NODE = "tithe";
    private static final String OTHER_TRADEOFF_NODE = "greedy_seams";
    private static final String NON_TRADEOFF_NODE = "vault_expansion";

    private static final boolean INSIDE_VAULT = true;
    private static final boolean OUTSIDE_VAULT = false;

    /** A tree with the given node purchased, bypassing level and point gates. */
    private static SkillTree treeWith(String nodeId) {
        SkillTree tree = new SkillTree(Tree.RESONANCE);
        tree.setUnlockedTier(nodeId, 1);
        return tree;
    }

    @Test
    void togglesOnThenOffOutsideTheVault() {
        SkillTree tree = treeWith(TRADEOFF_NODE);
        PlayerStats stats = new PlayerStats();

        assertEquals(TradeoffToggle.Result.ENABLED,
                TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT));
        assertTrue(stats.isTradeoffActive(TRADEOFF_NODE));

        assertEquals(TradeoffToggle.Result.DISABLED,
                TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT));
        assertFalse(stats.isTradeoffActive(TRADEOFF_NODE));
    }

    @Test
    void deniesEnablingInsideTheVault() {
        SkillTree tree = treeWith(TRADEOFF_NODE);
        PlayerStats stats = new PlayerStats();

        assertEquals(TradeoffToggle.Result.DENIED_INSIDE_VAULT,
                TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, INSIDE_VAULT));
    }

    /** The exploit this rule exists to close: take the upside, drop the cost mid-delve. */
    @Test
    void deniesDisablingInsideTheVault() {
        SkillTree tree = treeWith(TRADEOFF_NODE);
        PlayerStats stats = new PlayerStats();
        TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT);

        assertEquals(TradeoffToggle.Result.DENIED_INSIDE_VAULT,
                TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, INSIDE_VAULT));
    }

    @Test
    void denialInsideTheVaultLeavesToggleStateUnchanged() {
        SkillTree tree = treeWith(TRADEOFF_NODE);
        PlayerStats stats = new PlayerStats();
        TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT);

        TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, INSIDE_VAULT);

        assertTrue(stats.isTradeoffActive(TRADEOFF_NODE), "denied toggle must not mutate state");
    }

    @Test
    void deniesUnpurchasedNode() {
        SkillTree tree = new SkillTree(Tree.RESONANCE);
        PlayerStats stats = new PlayerStats();

        assertEquals(TradeoffToggle.Result.DENIED_NOT_PURCHASED,
                TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT));
        assertFalse(stats.isTradeoffActive(TRADEOFF_NODE));
    }

    @Test
    void rejectsNodeThatIsNotATradeoff() {
        SkillTree tree = treeWith(NON_TRADEOFF_NODE);
        PlayerStats stats = new PlayerStats();

        assertThrows(IllegalArgumentException.class,
                () -> TradeoffToggle.toggle(tree, stats, NON_TRADEOFF_NODE, OUTSIDE_VAULT));
    }

    @Test
    void togglesAreIndependentPerNode() {
        SkillTree tree = new SkillTree(Tree.RESONANCE);
        tree.setUnlockedTier(TRADEOFF_NODE, 1);
        tree.setUnlockedTier(OTHER_TRADEOFF_NODE, 1);
        PlayerStats stats = new PlayerStats();

        TradeoffToggle.toggle(tree, stats, TRADEOFF_NODE, OUTSIDE_VAULT);

        assertTrue(stats.isTradeoffActive(TRADEOFF_NODE));
        assertFalse(stats.isTradeoffActive(OTHER_TRADEOFF_NODE));
    }
}
