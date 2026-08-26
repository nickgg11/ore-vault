package com.orevault.orevault.skill;

/**
 * All skill point costs and level requirements as public constants, per the design spec
 * ("All costs and level requirements are defined as constants in a single NodeCosts.java
 * file for easy adjustment"). The dynamic level threshold calculator reads total tree cost
 * by summing all of these, so changing a constant automatically recalibrates the entire
 * level curve.
 */
public final class NodeCosts {
    private NodeCosts() {
    }

    // ===== Resonance tree =====

    // Branch: Core
    public static final int DISTURBED_ZONE_UNLOCK_COST = 1;
    public static final int DISTURBED_ZONE_UNLOCK_LEVEL_REQ = 0;

    // Branch: Vein
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

    // Branch: Ore Quality
    public static final int[] COMMON_ORE_BOOST_COSTS = {1, 1, 2};
    public static final int[] COMMON_ORE_BOOST_LEVEL_REQS = {1, 3, 6};
    public static final int[] UNCOMMON_ORE_BOOST_COSTS = {1, 2, 2};
    public static final int[] UNCOMMON_ORE_BOOST_LEVEL_REQS = {2, 5, 8};
    public static final int[] RARE_ORE_BOOST_COSTS = {2, 2, 3};
    public static final int[] RARE_ORE_BOOST_LEVEL_REQS = {5, 9, 13};
    public static final int[] ANCIENT_TRACES_COSTS = {5, 5};
    public static final int[] ANCIENT_TRACES_LEVEL_REQS = {12, 16};
    public static final int GRAVEL_PURGE_COST = 1;
    public static final int GRAVEL_PURGE_LEVEL_REQ = 1;
    public static final int[] STONE_REDUCTION_COSTS = {1, 2};
    public static final int[] STONE_REDUCTION_LEVEL_REQS = {3, 7};
    public static final int[] GEODE_CLUSTERS_COSTS = {1, 2};
    public static final int[] GEODE_CLUSTERS_LEVEL_REQS = {4, 8};

    // Branch: Fortune
    public static final int[] ORE_SENSE_COSTS = {2, 3, 4};
    public static final int[] ORE_SENSE_LEVEL_REQS = {5, 9, 13};
    public static final int[] ORE_DOUBLING_COSTS = {3, 3, 4, 7, 10, 15};
    public static final int[] ORE_DOUBLING_LEVEL_REQS = {8, 12, 16, 20, 25, 30};
    public static final int[] RUNIC_ATTUNEMENT_COSTS = {3, 3, 4};
    public static final int[] RUNIC_ATTUNEMENT_LEVEL_REQS = {10, 14, 17};
    public static final int[] SMELTERS_INTUITION_COSTS = {2, 2, 3};
    public static final int[] SMELTERS_INTUITION_LEVEL_REQS = {7, 11, 15};

    // Branch: XP and Stone
    public static final int[] STONE_MEMORY_COSTS = {1, 1, 2, 2, 3};
    public static final int[] STONE_MEMORY_LEVEL_REQS = {0, 3, 6, 10, 14};
    public static final int[] ANCIENT_KNOWLEDGE_COSTS = {1, 1, 2};
    public static final int[] ANCIENT_KNOWLEDGE_LEVEL_REQS = {2, 5, 9};

    // Branch: Hunger
    public static final int[] EFFICIENT_MINER_COSTS = {1, 1, 2, 2, 3};
    public static final int[] EFFICIENT_MINER_LEVEL_REQS = {0, 3, 6, 10, 15};

    // Branch: Utility
    public static final int[] RESONANCE_MAGNETISM_COSTS = {1, 1, 2};
    public static final int[] RESONANCE_MAGNETISM_LEVEL_REQS = {2, 5, 9};
    public static final int HOARDERS_INSTINCT_COST = 2;
    public static final int HOARDERS_INSTINCT_LEVEL_REQ = 4;
    public static final int[] AUTOMATED_EXTRACTION_COSTS = {2, 3};
    public static final int[] AUTOMATED_EXTRACTION_LEVEL_REQS = {8, 12};
    public static final int[] VAULT_PRESENCE_COSTS = {2, 2, 3};
    public static final int[] VAULT_PRESENCE_LEVEL_REQS = {5, 9, 14};
    public static final int VAULT_EXPANSION_COST = 10;
    public static final int VAULT_EXPANSION_LEVEL_REQ = 18;

    // Branch: FTB Ultimine (conditional)
    public static final int[] ULTIMINE_EXPANSION_COSTS = {1, 2, 3};
    public static final int[] ULTIMINE_EXPANSION_LEVEL_REQS = {3, 7, 12};
    public static final int[] ULTIMINE_SAFETY_COSTS = {1, 2};
    public static final int[] ULTIMINE_SAFETY_LEVEL_REQS = {5, 9};

    // Tradeoffs
    public static final int VOLATILE_VEINS_COST = 2;
    public static final int VOLATILE_VEINS_LEVEL_REQ = 6;
    public static final int ULTIMINE_GAMBIT_COST = 2;
    public static final int ULTIMINE_GAMBIT_LEVEL_REQ = 9;
    public static final int GREEDY_SEAMS_COST = 2;
    public static final int GREEDY_SEAMS_LEVEL_REQ = 5;
    public static final int STONE_CURSE_COST = 2;
    public static final int STONE_CURSE_LEVEL_REQ = 4;
    public static final int VAULT_FEVER_COST = 2;
    public static final int VAULT_FEVER_LEVEL_REQ = 7;
    public static final int TITHE_COST = 2;
    public static final int TITHE_LEVEL_REQ = 5;

    // Exclusive pairs
    public static final int[] ABUNDANCE_COSTS = {3, 4};
    public static final int[] ABUNDANCE_LEVEL_REQS = {7, 12};
    public static final int[] MOTHERLODE_COSTS = {3, 4};
    public static final int[] MOTHERLODE_LEVEL_REQS = {7, 12};
    public static final int VAULTS_BLESSING_COST = 2;
    public static final int VAULTS_BLESSING_LEVEL_REQ = 8;
    public static final int VAULTS_PURITY_COST = 2;
    public static final int VAULTS_PURITY_LEVEL_REQ = 8;

    // ===== Animus (mob) tree =====

    public static final int[] ZONE_FREQUENCY_COSTS = {1, 1, 2, 2};
    public static final int[] ZONE_FREQUENCY_LEVEL_REQS = {0, 3, 6, 10};
    public static final int[] ZONE_PACK_SIZE_COSTS = {1, 1, 2};
    public static final int[] ZONE_PACK_SIZE_LEVEL_REQS = {1, 4, 8};
    public static final int[] ZONE_RADIUS_COSTS = {1, 2, 3};
    public static final int[] ZONE_RADIUS_LEVEL_REQS = {2, 6, 11};
    public static final int[] MOB_DIVERSITY_COSTS = {1, 1, 2, 3};
    public static final int[] MOB_DIVERSITY_LEVEL_REQS = {0, 3, 7, 12};
    public static final int[] REAPERS_CLAIM_COSTS = {1, 2, 2};
    public static final int[] REAPERS_CLAIM_LEVEL_REQS = {2, 5, 9};
    public static final int[] CORRUPTED_VEINS_COSTS = {1, 2, 3};
    public static final int[] CORRUPTED_VEINS_LEVEL_REQS = {3, 7, 12};
    public static final int[] PLUNDERERS_SHARE_COSTS = {1, 1, 2};
    public static final int[] PLUNDERERS_SHARE_LEVEL_REQS = {2, 5, 9};
    public static final int[] ANIMUS_AMPLIFIER_COSTS = {1, 1, 2};
    public static final int[] ANIMUS_AMPLIFIER_LEVEL_REQS = {1, 4, 8};
    public static final int SOUL_HARVEST_COST = 5;
    public static final int SOUL_HARVEST_LEVEL_REQ = 15;

    // ===== Derived balance constants =====

    /** Hard stone floor: the Vault is never more than 60% ore by volume. */
    public static final double MAX_ORE_FRACTION = 0.60;
    /** Effective team multiplier curve: 1 + (teamSize - 1) * 0.7. */
    public static final double TEAM_MULTIPLIER_STEP = 0.7;
    /** Default target play hours for full Resonance tree completion. */
    public static final double TARGET_PLAY_HOURS_RESONANCE = 100;
    /** Default target play hours for full Animus tree completion. */
    public static final double TARGET_PLAY_HOURS_ANIMUS = 100;
    /** Assumed average team size for the level curve calibration. */
    public static final double ASSUMED_TEAM_SIZE = 2.5;
    /** Assumed ores mined per player-hour in the Vault. */
    public static final double AVERAGE_ORES_PER_HOUR = 1200;
    /** Weighted average Resonance per ore (mix of common 2 / uncommon 5 / rare 10-15). */
    public static final double WEIGHTED_AVERAGE_RESONANCE_PER_ORE = 3.0;
    /** Assumed zone mob kills per player-hour. */
    public static final double AVERAGE_KILLS_PER_HOUR = 400;
    /** Weighted average Animus per kill (common 3 / uncommon 8 / rare 20). */
    public static final double WEIGHTED_AVERAGE_ANIMUS_PER_KILL = 5.0;
    /** XP levels required to refund a fully invested tree (scaled by investment). */
    public static final int MAX_REFUND_XP_LEVELS = 50;
}
