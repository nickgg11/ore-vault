package com.orevault.orevault.skill;

import com.orevault.orevault.data.OreVaultTeamData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Central skill tree logic: unlock validation (level req, prerequisites, exclusives,
 * points), spending, and XP-scaled refunds. Effects themselves live in the systems that
 * care (worldgen, drop handler, tick handlers, zone manager).
 */
public final class SkillTree {
    private SkillTree() {
    }

    public record UnlockResult(boolean ok, Component message) {
        public static UnlockResult success() {
            return new UnlockResult(true, Component.empty());
        }

        public static UnlockResult fail(String langKey) {
            return new UnlockResult(false, Component.translatable(langKey));
        }
    }

    public static LevelCurve resonanceCurve() {
        return Curves.RESONANCE;
    }

    public static LevelCurve animusCurve() {
        return Curves.ANIMUS;
    }

    public static int treeLevel(OreVaultTeamData data, String treeId) {
        return NodeDefs.RESONANCE.equals(treeId) ? data.resonanceLevel() : data.animusLevel();
    }

    public static int treePoints(OreVaultTeamData data, String treeId) {
        return NodeDefs.RESONANCE.equals(treeId) ? data.resonanceSkillPoints() : data.animusSkillPoints();
    }

    /**
     * Validates that the team may unlock the next tier of the given node.
     */
    public static UnlockResult canUnlock(OreVaultTeamData data, NodeDef node) {
        int currentTier = data.nodeTier(node.id());
        int nextTier = currentTier + 1;
        if (!node.hasTier(nextTier)) {
            return UnlockResult.fail("orevault.msg.max_tier");
        }
        if (treePoints(data, node.treeId()) < node.costs()[nextTier - 1]) {
            return UnlockResult.fail("orevault.msg.need_points");
        }
        int level = treeLevel(data, node.treeId());
        if (level < node.levelReqs()[nextTier - 1]) {
            return UnlockResult.fail("orevault.msg.need_level");
        }
        for (String prereq : node.prereqs()[nextTier - 1]) {
            String[] parts = prereq.split(":", 2);
            int needTier = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
            if (data.nodeTier(parts[0]) < needTier) {
                return UnlockResult.fail("orevault.msg.need_prereq");
            }
        }
        for (String exclusive : node.exclusives()) {
            if (data.hasNode(exclusive)) {
                return UnlockResult.fail("orevault.msg.exclusive_conflict");
            }
        }
        return UnlockResult.success();
    }

    /**
     * Unlocks the next tier of the node, deducting skill points. Caller must have called
     * {@link #canUnlock} first (this method does not re-validate side conditions).
     */
    public static boolean unlock(OreVaultTeamData data, NodeDef node) {
        int nextTier = data.nodeTier(node.id()) + 1;
        if (!node.hasTier(nextTier)) {
            return false;
        }
        int cost = node.costs()[nextTier - 1];
        int points = treePoints(data, node.treeId());
        if (points < cost) {
            return false;
        }
        if (NodeDefs.RESONANCE.equals(node.treeId())) {
            data.setResonanceSkillPoints(points - cost);
        } else {
            data.setAnimusSkillPoints(points - cost);
        }
        data.unlockNode(node.id(), nextTier);
        return true;
    }

    /**
     * Refund cost in XP levels: proportional to how invested the tree is.
     * cost = round((invested / totalTreeCost) * MAX_REFUND_XP_LEVELS)
     */
    public static int refundXpCost(OreVaultTeamData data, String treeId) {
        int total = NodeDefs.totalTreeCost(treeId);
        if (total <= 0) {
            return 0;
        }
        int invested = data.totalSkillPointsInvested(treeId);
        double frac = (double) invested / (double) total;
        return (int) Math.ceil(frac * NodeCosts.MAX_REFUND_XP_LEVELS);
    }

    public static boolean canRefund(OreVaultTeamData data, ServerPlayer player, String treeId, NodeDef node) {
        if (data.nodeTier(node.id()) <= 0) {
            return false;
        }
        // Cannot refund a node that others depend on.
        for (NodeDef other : NodeDefs.forTree(treeId)) {
            if (data.hasNode(other.id())) {
                for (String[] tierPrereqs : other.prereqs()) {
                    for (String prereq : tierPrereqs) {
                        if (prereq.split(":", 2)[0].equals(node.id())) {
                            return false;
                        }
                    }
                }
            }
        }
        return player.experienceLevel >= refundXpCost(data, treeId);
    }

    /**
     * Refunds the top tier of the given node, returning one skill point and charging XP.
     */
    public static boolean refund(OreVaultTeamData data, ServerPlayer player, NodeDef node) {
        int tier = data.nodeTier(node.id());
        if (tier <= 0) {
            return false;
        }
        int cost = refundXpCost(data, node.treeId());
        if (player.experienceLevel < cost) {
            return false;
        }
        player.giveExperienceLevels(-cost);
        if (tier > 1) {
            data.unlockNode(node.id(), tier - 1);
        } else {
            data.removeNode(node.id());
        }
        if (NodeDefs.RESONANCE.equals(node.treeId())) {
            data.addResonanceSkillPoints(1);
        } else {
            data.addAnimusSkillPoints(1);
        }
        return true;
    }

    /** Applies pooled Resonance to the level track, awarding skill points for level-ups. */
    public static int applyResonanceLevels(OreVaultTeamData data) {
        int newLevel = resonanceCurve().levelForPool(data.resonancePool());
        int gained = newLevel - data.resonanceLevel();
        if (gained > 0) {
            data.setResonanceLevel(newLevel);
            data.addResonanceSkillPoints(gained);
        }
        return gained;
    }

    /** Applies pooled Animus to the level track, awarding skill points for level-ups. */
    public static int applyAnimusLevels(OreVaultTeamData data) {
        int newLevel = animusCurve().levelForPool(data.animusPool());
        int gained = newLevel - data.animusLevel();
        if (gained > 0) {
            data.setAnimusLevel(newLevel);
            data.addAnimusSkillPoints(gained);
        }
        return gained;
    }

    /**
     * Computed once per server start per the spec.
     */
    public static final class Curves {
        public static final LevelCurve RESONANCE = LevelCurve.compute(
                NodeDefs.totalTreeCost(NodeDefs.RESONANCE),
                NodeCosts.TARGET_PLAY_HOURS_RESONANCE,
                NodeCosts.ASSUMED_TEAM_SIZE,
                NodeCosts.AVERAGE_ORES_PER_HOUR,
                NodeCosts.WEIGHTED_AVERAGE_RESONANCE_PER_ORE
        );
        public static final LevelCurve ANIMUS = LevelCurve.compute(
                NodeDefs.totalTreeCost(NodeDefs.ANIMUS),
                NodeCosts.TARGET_PLAY_HOURS_ANIMUS,
                NodeCosts.ASSUMED_TEAM_SIZE,
                NodeCosts.AVERAGE_KILLS_PER_HOUR,
                NodeCosts.WEIGHTED_AVERAGE_ANIMUS_PER_KILL
        );

        private Curves() {
        }
    }

    // --- Convenience for UI sync ------------------------------------------------

    public record TreeStateSnapshot(int level, int points, long pool, long nextThreshold, long levelCost) {
    }

    public static TreeStateSnapshot snapshot(OreVaultTeamData data, String treeId) {
        LevelCurve curve = NodeDefs.RESONANCE.equals(treeId) ? resonanceCurve() : animusCurve();
        int level = treeLevel(data, treeId);
        long pool = NodeDefs.RESONANCE.equals(treeId) ? data.resonancePool() : data.animusPool();
        long nextThreshold = level >= curve.maxLevel() ? -1 : curve.thresholdForLevel(level + 1);
        return new TreeStateSnapshot(level, treePoints(data, treeId), pool, nextThreshold, curve.levelCost(level + 1));
    }
}
