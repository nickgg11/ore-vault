package com.orevault.orevault.skill;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rewrites a team's persisted node tiers when node ids change (§6.1).
 *
 * <p>Unlocked tiers are stored keyed by node id in {@code OreVaultTeamData}, which makes every
 * rename and removal in the tree a save-data change rather than a refactor. A team that bought Ore
 * Sense to tier 3 must find Vein Fortune at tier 3; a team that bought the three Ore Boosts must
 * get their points back rather than paying for nodes this build will not let them buy.</p>
 *
 * <h2>Why the old costs live here</h2>
 *
 * <p>Refunding a removed node means pricing tiers that {@link NodeCosts} no longer describes. A
 * migration has to carry the shape it migrates <em>from</em>, so those arrays are frozen copies
 * kept in this class and must never be "tidied up" to point at current constants — the moment they
 * track live values they stop describing the save they are reading.</p>
 *
 * <h2>Pure on purpose</h2>
 *
 * <p>Takes and returns a plain map so it can be tested. {@code OreVaultTeamData} adapts NBT to it;
 * nothing here touches a Minecraft type.</p>
 */
public final class NodeMigration {

    /**
     * The outcome of a migration.
     *
     * @param tiers           node tiers under this build's ids, ready to load
     * @param pointsRefunded  skill points to return to the team's unspent pool
     */
    public record Result(Map<String, Integer> tiers, int pointsRefunded) {
        public Result {
            tiers = Map.copyOf(tiers);
        }
    }

    /** Old id to new id. The tier carries across unchanged. */
    private static final Map<String, String> RENAMED = Map.of(
            // "Ore Sense" described a sensing mechanic it never had; the name now belongs to
            // Prospector's Eye, and the node itself was always a passive Fortune.
            "ore_sense", "vein_fortune",
            // The Hunger branch is gone; hunger is one line in the Prospecting survival group.
            "efficient_miner", "miners_constitution");

    /**
     * Removed ids and the per-tier costs they carried when they existed.
     *
     * <p>Frozen. These are the numbers the save was written under, not the numbers the tree uses
     * now, and they are the only honest basis for a refund.</p>
     */
    private static final Map<String, int[]> REMOVED_COSTS = Map.of(
            // Replaced by Ore Attunement plus three free Focus options: the old chain stacked, so
            // everyone bought all three in the same order and nothing was ever decided.
            "common_ore_boost", new int[]{1, 1, 2},
            "uncommon_ore_boost", new int[]{1, 2, 2},
            "rare_ore_boost", new int[]{2, 2, 3},
            // Mechanically identical to Vein Expansion under a different name. Its successor,
            // Vein Singularity, is a free option under a parent this team never paid for, so this
            // is a refund and not a mapping.
            "motherlode", new int[]{3, 4},
            // Deferred with the rest of the Animus system to the post-1.0 epic (#90).
            "disturbed_zone_unlock", new int[]{1});

    private NodeMigration() {
    }

    /**
     * Applies every §6.1 rename and removal to {@code stored}.
     *
     * <p>Idempotent: running it on its own output changes nothing and refunds nothing, which
     * matters because a migration that double-pays is worse than one that never runs.</p>
     *
     * @param stored node id to unlocked tier, as read from the save
     */
    public static Result apply(Map<String, Integer> stored) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int refunded = 0;

        for (Map.Entry<String, Integer> entry : stored.entrySet()) {
            String id = entry.getKey();
            int tier = entry.getValue();
            if (tier <= 0) {
                continue;
            }

            int[] removedCosts = REMOVED_COSTS.get(id);
            if (removedCosts != null) {
                for (int i = 0; i < Math.min(tier, removedCosts.length); i++) {
                    refunded += removedCosts[i];
                }
                continue;
            }

            String target = RENAMED.getOrDefault(id, id);
            NodeDef def = NodeDefs.get(target);
            if (def == null) {
                // No cost table and no definition. Inventing a refund would hand out points the
                // team never spent, so the tier is dropped and the points stay where they are.
                continue;
            }
            // Clamped because a save can legitimately hold a tier this build no longer offers, and
            // loading one unclamped puts costs() out of bounds the first time it is priced.
            int clamped = Math.min(tier, def.maxTier());
            out.merge(target, clamped, Math::max);
        }

        return new Result(out, refunded);
    }

    /** Whether {@code stored} contains anything this migration would change. */
    public static boolean isNeeded(Map<String, Integer> stored) {
        for (String id : stored.keySet()) {
            if (RENAMED.containsKey(id) || REMOVED_COSTS.containsKey(id) || NodeDefs.get(id) == null) {
                return true;
            }
        }
        return false;
    }
}
