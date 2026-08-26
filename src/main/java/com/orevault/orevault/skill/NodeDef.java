package com.orevault.orevault.skill;

import java.util.Optional;

/**
 * One skill node definition. Covers every node in the design spec section 6.
 */
public record NodeDef(
        String id,
        String treeId,          // "resonance" or "animus"
        String branch,          // display grouping (Vein, Ore Quality, ...)
        String displayNameKey,  // lang key
        String descriptionKey,  // lang key
        int maxTier,
        int[] costs,            // skill point cost per tier
        int[] levelReqs,        // team level requirement per tier
        String[][] prereqs,     // node ids required before unlocking each tier
        boolean tradeoff,       // [TRADEOFF] toggleable per player
        String[] exclusives,    // [EXCLUSIVE: X] conflicting node ids
        boolean ultimineOnly,   // [ULTIMINE] hidden when FTB Ultimine absent
        boolean mekanismOnlyTiers, // extra tiers hidden when Mekanism absent
        boolean keystone         // keystone node (Vault Expansion / Soul Harvest)
) {
    public int tierCount() {
        return costs.length;
    }

    public boolean hasTier(int tier) {
        return tier >= 1 && tier <= costs.length;
    }

    public boolean isHidden() {
        return ultimineOnly && !SoftDeps.isUltimineLoaded();
    }

    public int visibleTierCount() {
        if (mekanismOnlyTiers && !SoftDeps.isMekanismLoaded()) {
            return 3; // base tiers only
        }
        return costs.length;
    }

    public static Optional<NodeDef> byId(String id) {
        return NodeDefs.get(id);
    }
}
