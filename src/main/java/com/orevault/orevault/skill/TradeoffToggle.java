package com.orevault.orevault.skill;

import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative gate for the §6.1 tradeoff toggles.
 *
 * <p>Tradeoffs are a loadout the player commits to <em>before</em> they delve:
 * toggling is free and unlimited outside the Vault, and rejected inside it.
 * Without that restriction a tradeoff carries no commitment at all — a player
 * enables Tithe while mining ore and disables it before touching stone, taking
 * every upside and paying none of the cost, which flattens the mechanic
 * entirely.</p>
 *
 * <p>The decision lives here rather than in {@link SkillTree} so the tree stays
 * pure: it knows about nodes and costs, not about dimensions. The core
 * {@link #toggle(SkillTree, PlayerStats, String, boolean)} takes the
 * inside-the-Vault answer as a plain boolean and is fully unit-tested; the
 * {@link ServerPlayer} overload is a zero-logic adapter that resolves that
 * boolean from the player's current level.</p>
 *
 * <p><strong>This is the enforcement point, not a UI hint.</strong> The Tome
 * ({@code [34]}) should grey the toggle out with a reason while inside, but the
 * screen is client-side and a crafted packet must not be able to bypass it.
 * Every server-side toggle path routes through this class.</p>
 */
public final class TradeoffToggle {

    private TradeoffToggle() {
    }

    /** Outcome of a toggle attempt. */
    public enum Result {
        /** The tradeoff is now active. */
        ENABLED,
        /** The tradeoff is now inactive. */
        DISABLED,
        /** Rejected: the player is inside a Vault dimension (§6.1). */
        DENIED_INSIDE_VAULT,
        /** Rejected: the node has not been purchased. */
        DENIED_NOT_PURCHASED;

        /** Whether the toggle actually happened. */
        public boolean allowed() {
            return this == ENABLED || this == DISABLED;
        }
    }

    /**
     * Toggles a purchased tradeoff node for one player.
     *
     * @param tree        the team's tree, used to check the node is purchased
     * @param stats       the player's stats, which own the toggle state (§6.1:
     *                    "toggle state is saved per-player")
     * @param nodeId      the tradeoff node to toggle
     * @param insideVault whether the player is currently inside a Vault dimension
     * @return the outcome; state is left untouched for any {@code DENIED_*} result
     * @throws IllegalArgumentException if {@code nodeId} is not a tradeoff node
     */
    public static Result toggle(SkillTree tree, PlayerStats stats, String nodeId, boolean insideVault) {
        NodeDef def = NodeDefs.get(nodeId);
        if (def == null || !def.tradeoff()) {
            throw new IllegalArgumentException("Not a tradeoff node: " + nodeId);
        }
        if (insideVault) {
            return Result.DENIED_INSIDE_VAULT;
        }
        if (!tree.isUnlocked(nodeId)) {
            return Result.DENIED_NOT_PURCHASED;
        }
        return stats.toggleTradeoff(nodeId) ? Result.ENABLED : Result.DISABLED;
    }

    /**
     * Toggles a tradeoff for a player, resolving the Vault check from the level
     * they are currently in. Zero logic of its own — see the core overload.
     */
    public static Result toggle(ServerPlayer player, SkillTree tree, PlayerStats stats, String nodeId) {
        return toggle(tree, stats, nodeId, VaultDimensions.isVaultDimension(player.level()));
    }
}
