package com.orevault.orevault.skill;

/**
 * Central store for every skill point cost, level requirement, and shared tuning
 * constant defined in {@code OreVault_Design_and_Spec.md} (§6 node tables, §4.2
 * Resonance gain, §5.1 Animus gain, §10 derived values).
 *
 * <p>This class is intentionally dependency-free so it can be read by the node
 * registry ({@code NodeDefs}), the skill tree, the level-curve calculator, and
 * world-gen code without pulling in any game types.</p>
 *
 * <p>Every tiered node exposes a {@code COSTS} array (skill points per tier) and
 * a parallel {@code LEVEL_REQS} array (minimum team level per tier). The arrays
 * are the same length and index 0 is tier 1.</p>
 */
public final class NodeCosts {

    private NodeCosts() {
    }

    // =====================================================================
    // Shared tuning constants
    // =====================================================================

    /** Minimum fraction of generated Vault blocks that must remain stone (§6.1). */
    public static final double STONE_CONTENT_FLOOR = 0.40;

    /**
     * Per-extra-member coordination bonus in the team pool multiplier (§4.2):
     * {@code 1 + 0.1 * (teamSize - 1)}. Small on purpose — the pool is divided
     * by team size first, so joining a team must not outpace playing solo.
     */
    public static final double TEAM_SIZE_COORDINATION_STEP = 0.1;

    /**
     * Level cap for both the Resonance and Animus tracks (§4.3, §5.2).
     *
     * <p>Levels are decoupled from skill points: the cap is fixed and each level
     * awards {@code ceil(totalTreeCost / LEVEL_CAP)} points. Setting the cap to
     * the tree's total cost instead (225 for Resonance) put every level gate in
     * §6 — the highest of which is 30 — inside the first hour of a 100-hour
     * curve.</p>
     */
    public static final int LEVEL_CAP = 30;

    /** Default target play hours for the Resonance tree (§4.3). */
    public static final int TARGET_PLAY_HOURS_RESONANCE = 100;

    /** Default target play hours for the Animus tree (§5.2). */
    public static final int TARGET_PLAY_HOURS_ANIMUS = 100;

    /** XP levels charged per skill point when refunding a node tier (§4.4). */
    public static final int REFUND_XP_PER_POINT = 3;

    /** Length of the free-respec window opened by a dimension reset, in ticks (§3.5, §4.4). */
    public static final long FREE_RESPEC_WINDOW_TICKS = 10L * 60L * 20L;

    // Resonance gain base values (§4.2)
    public static final int RESONANCE_COMMON = 2;
    public static final int RESONANCE_UNCOMMON = 5;
    public static final int RESONANCE_RARE_MIN = 10;
    public static final int RESONANCE_RARE_MAX = 15;
    public static final double STONE_MEMORY_RESONANCE = 0.5;
    public static final int VAULT_ECHO_BURST_MIN = 25;
    public static final int VAULT_ECHO_BURST_MAX = 40;

    // Animus gain base values (§5.1)
    public static final int ANIMUS_COMMON = 3;
    public static final int ANIMUS_UNCOMMON = 8;
    public static final int ANIMUS_RARE = 20;

    // Orb collection radius (§4.2 base, §6.1 Resonance Magnetism tiers)
    public static final int ORB_BASE_RADIUS = 8;
    public static final int[] RESONANCE_MAGNETISM_RADII = {8, 16, 24};

    // Volatile Veins pity system (§11)
    public static final int VOLATILE_VEINS_TRIGGER_STREAK_MAX = 3;
    public static final int VOLATILE_VEINS_SAFE_BLOCKS = 10;

    // =====================================================================
    // Resonance tree — Core
    // =====================================================================

    public static final int[] DISTURBED_ZONE_UNLOCK_COSTS = {1};
    public static final int[] DISTURBED_ZONE_UNLOCK_LEVEL_REQS = {0};

    // =====================================================================
    // Resonance tree — Vein branch
    // =====================================================================

    public static final int[] VEIN_EXPANSION_COSTS = {1, 1, 2, 2, 3};
    public static final int[] VEIN_EXPANSION_LEVEL_REQS = {0, 2, 4, 7, 10};

    public static final int[] VEIN_PROLIFERATION_COSTS = {1, 1, 2, 2, 3};
    public static final int[] VEIN_PROLIFERATION_LEVEL_REQS = {2, 4, 7, 10, 14};

    public static final int[] DEEP_VEINS_COSTS = {2, 3};
    public static final int[] DEEP_VEINS_LEVEL_REQS = {5, 9};

    public static final int[] TWIN_VEINS_COSTS = {2, 2, 3};
    public static final int[] TWIN_VEINS_LEVEL_REQS = {6, 10, 14};

    public static final int[] VAULT_ECHO_COSTS = {1, 1, 2};
    public static final int[] VAULT_ECHO_LEVEL_REQS = {3, 6, 9};

    // =====================================================================
    // Resonance tree — Ore Quality branch
    // =====================================================================

    public static final int[] COMMON_ORE_BOOST_COSTS = {1, 1, 2};
    public static final int[] COMMON_ORE_BOOST_LEVEL_REQS = {1, 3, 6};

    public static final int[] UNCOMMON_ORE_BOOST_COSTS = {1, 2, 2};
    public static final int[] UNCOMMON_ORE_BOOST_LEVEL_REQS = {2, 5, 8};

    public static final int[] RARE_ORE_BOOST_COSTS = {2, 2, 3};
    public static final int[] RARE_ORE_BOOST_LEVEL_REQS = {5, 9, 13};

    public static final int[] ANCIENT_TRACES_COSTS = {5, 5};
    public static final int[] ANCIENT_TRACES_LEVEL_REQS = {12, 16};

    public static final int[] GRAVEL_PURGE_COSTS = {1};
    public static final int[] GRAVEL_PURGE_LEVEL_REQS = {1};

    public static final int[] STONE_REDUCTION_COSTS = {1, 2};
    public static final int[] STONE_REDUCTION_LEVEL_REQS = {3, 7};

    public static final int[] GEODE_CLUSTERS_COSTS = {1, 2};
    public static final int[] GEODE_CLUSTERS_LEVEL_REQS = {4, 8};

    // =====================================================================
    // Resonance tree — Fortune branch
    // =====================================================================

    public static final int[] ORE_SENSE_COSTS = {2, 3, 4};
    public static final int[] ORE_SENSE_LEVEL_REQS = {5, 9, 13};

    public static final int[] ORE_DOUBLING_COSTS = {3, 3, 4, 7, 10, 15};
    public static final int[] ORE_DOUBLING_LEVEL_REQS = {8, 12, 16, 20, 25, 30};

    public static final int[] RUNIC_ATTUNEMENT_COSTS = {3, 3, 4};
    public static final int[] RUNIC_ATTUNEMENT_LEVEL_REQS = {10, 14, 17};

    public static final int[] SMELTERS_INTUITION_COSTS = {2, 2, 3};
    public static final int[] SMELTERS_INTUITION_LEVEL_REQS = {7, 11, 15};

    // =====================================================================
    // Resonance tree — XP and Stone branch
    // =====================================================================

    public static final int[] STONE_MEMORY_COSTS = {1, 1, 2, 2, 3};
    public static final int[] STONE_MEMORY_LEVEL_REQS = {0, 3, 6, 10, 14};

    public static final int[] ANCIENT_KNOWLEDGE_COSTS = {1, 1, 2};
    public static final int[] ANCIENT_KNOWLEDGE_LEVEL_REQS = {2, 5, 9};

    // =====================================================================
    // Resonance tree — Hunger branch
    // =====================================================================

    public static final int[] EFFICIENT_MINER_COSTS = {1, 1, 2, 2, 3};
    public static final int[] EFFICIENT_MINER_LEVEL_REQS = {0, 3, 6, 10, 15};

    // =====================================================================
    // Resonance tree — Utility branch
    // =====================================================================

    public static final int[] RESONANCE_MAGNETISM_COSTS = {1, 1, 2};
    public static final int[] RESONANCE_MAGNETISM_LEVEL_REQS = {2, 5, 9};

    public static final int[] HOARDERS_INSTINCT_COSTS = {2};
    public static final int[] HOARDERS_INSTINCT_LEVEL_REQS = {4};

    public static final int[] AUTOMATED_EXTRACTION_COSTS = {2, 3};
    public static final int[] AUTOMATED_EXTRACTION_LEVEL_REQS = {8, 12};

    public static final int[] VAULT_PRESENCE_COSTS = {2, 2, 3};
    public static final int[] VAULT_PRESENCE_LEVEL_REQS = {5, 9, 14};

    public static final int[] VAULT_EXPANSION_COSTS = {10};
    public static final int[] VAULT_EXPANSION_LEVEL_REQS = {18};

    // =====================================================================
    // Resonance tree — FTB Ultimine branch (hidden when Ultimine absent)
    // =====================================================================

    public static final int[] ULTIMINE_EXPANSION_COSTS = {1, 2, 3};
    public static final int[] ULTIMINE_EXPANSION_LEVEL_REQS = {3, 7, 12};

    public static final int[] ULTIMINE_SAFETY_COSTS = {1, 2};
    public static final int[] ULTIMINE_SAFETY_LEVEL_REQS = {5, 9};

    // =====================================================================
    // Resonance tree — Tradeoff nodes
    // =====================================================================

    public static final int[] VOLATILE_VEINS_COSTS = {2};
    public static final int[] VOLATILE_VEINS_LEVEL_REQS = {6};

    public static final int[] ULTIMINE_GAMBIT_COSTS = {2};
    public static final int[] ULTIMINE_GAMBIT_LEVEL_REQS = {9};

    public static final int[] GREEDY_SEAMS_COSTS = {2};
    public static final int[] GREEDY_SEAMS_LEVEL_REQS = {5};

    public static final int[] STONE_CURSE_COSTS = {2};
    public static final int[] STONE_CURSE_LEVEL_REQS = {4};

    public static final int[] VAULT_FEVER_COSTS = {2};
    public static final int[] VAULT_FEVER_LEVEL_REQS = {7};

    public static final int[] TITHE_COSTS = {2};
    public static final int[] TITHE_LEVEL_REQS = {5};

    // =====================================================================
    // Resonance tree — Exclusive node pairs
    // =====================================================================

    public static final int[] ABUNDANCE_COSTS = {3, 4};
    public static final int[] ABUNDANCE_LEVEL_REQS = {7, 12};

    public static final int[] MOTHERLODE_COSTS = {3, 4};
    public static final int[] MOTHERLODE_LEVEL_REQS = {7, 12};

    public static final int[] VAULTS_BLESSING_COSTS = {2};
    public static final int[] VAULTS_BLESSING_LEVEL_REQS = {8};

    public static final int[] VAULTS_PURITY_COSTS = {2};
    public static final int[] VAULTS_PURITY_LEVEL_REQS = {8};

    // =====================================================================
    // Animus (Mob) tree — Disturbed Zone Enhancement branch
    // =====================================================================

    public static final int[] ZONE_FREQUENCY_COSTS = {1, 1, 2, 2};
    public static final int[] ZONE_FREQUENCY_LEVEL_REQS = {0, 3, 6, 10};

    public static final int[] ZONE_PACK_SIZE_COSTS = {1, 1, 2};
    public static final int[] ZONE_PACK_SIZE_LEVEL_REQS = {1, 4, 8};

    public static final int[] ZONE_RADIUS_COSTS = {1, 2, 3};
    public static final int[] ZONE_RADIUS_LEVEL_REQS = {2, 6, 11};

    public static final int[] MOB_DIVERSITY_COSTS = {1, 1, 2, 3};
    public static final int[] MOB_DIVERSITY_LEVEL_REQS = {0, 3, 7, 12};

    // =====================================================================
    // Animus (Mob) tree — Mob Rewards branch
    // =====================================================================

    public static final int[] REAPERS_CLAIM_COSTS = {1, 2, 2};
    public static final int[] REAPERS_CLAIM_LEVEL_REQS = {2, 5, 9};

    public static final int[] CORRUPTED_VEINS_COSTS = {1, 2, 3};
    public static final int[] CORRUPTED_VEINS_LEVEL_REQS = {3, 7, 12};

    public static final int[] PLUNDERERS_SHARE_COSTS = {1, 1, 2};
    public static final int[] PLUNDERERS_SHARE_LEVEL_REQS = {2, 5, 9};

    public static final int[] ANIMUS_AMPLIFIER_COSTS = {1, 1, 2};
    public static final int[] ANIMUS_AMPLIFIER_LEVEL_REQS = {1, 4, 8};

    public static final int[] SOUL_HARVEST_COSTS = {5};
    public static final int[] SOUL_HARVEST_LEVEL_REQS = {15};
}
