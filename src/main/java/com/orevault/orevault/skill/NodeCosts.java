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
 * are the same length and index 0 is tier 1. Fork options cost {@code {0}} by
 * definition (§6.1) and carry only a level requirement matching their parent's
 * first tier.</p>
 *
 * <p>Sections follow the §6.1 clusters, in the order they are drawn down the tree.
 * The old branch grouping is gone: keeping two orderings — one here and one in the
 * spec — is how the code and the design drifted apart in the first place.</p>
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
     * the tree's total cost instead put every level gate in §6 — the highest of
     * which is 30 — inside the first hour of a 100-hour curve.</p>
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
    // Vault geometry the nodes depend on (§3.1)
    // =====================================================================

    /**
     * Base of the dirt band in every Vault layer stack, and therefore the line above
     * which Bedrock Communion's penalty does not apply (§6.1).
     *
     * <p>Dirt is Y=246–249, grass Y=250, open air Y=251–319. Everything a player builds
     * on or above the surface sits at or above this line, which is the entire reason
     * the keystone is playable: without the exemption it taxes the one part of the
     * Vault that gets built in.</p>
     */
    public static final int VAULT_DIRT_BAND_MIN_Y = 246;

    /** Lowest non-bedrock Y in an expanded Vault. Bedrock itself is Y=-64. */
    public static final int VAULT_EXPANDED_FLOOR_Y = -63;

    // =====================================================================
    // Breakpoint and keystone tuning (§6.1)
    // =====================================================================

    /** Calloused Hands: per-tier coefficient on {@code ln(1 + blocks / SCALE)}. */
    public static final double[] CALLOUSED_HANDS_COEFFICIENTS = {0.03, 0.05, 0.07};

    /** Calloused Hands: block count that makes the logarithm's argument 2. */
    public static final double CALLOUSED_HANDS_SCALE = 10_000.0;

    /**
     * Calloused Hands: hard ceiling on the bonus.
     *
     * <p>The curve reaches it at roughly 10.8 million blocks and therefore never in
     * practice. It exists so that no future coefficient change can turn a lifetime
     * counter into an unbounded multiplier — the failure mode of the linear version
     * this replaced, which reached x30 over a normal progression.</p>
     */
    public static final double CALLOUSED_HANDS_CAP = 0.40;

    /** Highwater Mark: depth record at or above which only the flat 1 durability applies. */
    public static final int HIGHWATER_MARK_BASELINE_Y = 64;

    /** Highwater Mark: blocks of depth record per extra point of durability repaired. */
    public static final int HIGHWATER_MARK_BLOCKS_PER_POINT = 16;

    /** Highwater Mark: maximum durability repaired per ore, reached at expanded bedrock. */
    public static final int HIGHWATER_MARK_MAX_REPAIR = 9;

    /** Vein Discipline: Resonance bonus per completed vein in the streak. */
    public static final double VEIN_DISCIPLINE_PER_VEIN = 0.02;

    /** Vein Discipline: streak length at which the bonus caps. */
    public static final int VEIN_DISCIPLINE_MAX_STREAK = 15;

    /**
     * Vein Discipline: streak lost when a vein is abandoned.
     *
     * <p>Three, not the whole streak. A hard reset means one stray swing at the edge of
     * a vein you had not noticed costs fifteen veins of work, and the rational response
     * to that is to stop exploring and mine defensively — the opposite of what the tree
     * is trying to encourage.</p>
     */
    public static final int VEIN_DISCIPLINE_ABANDON_PENALTY = 3;

    /** Vein Discipline: distance from a part-mined vein that counts as abandoning it. */
    public static final int VEIN_DISCIPLINE_ABANDON_DISTANCE = 48;

    /** Deep Habit: blocks per trip per bonus step, and the step and cap (§6.1). */
    public static final int DEEP_HABIT_BLOCKS_PER_STEP = 1_000;
    public static final double DEEP_HABIT_STEP = 0.05;
    public static final double DEEP_HABIT_CAP = 0.25;

    /** Long Delve: minutes per stack, per-stack effects, and the tier-1/tier-2 stack caps. */
    public static final int LONG_DELVE_MINUTES_PER_STACK = 20;
    public static final double LONG_DELVE_MINING_SPEED_PER_STACK = 0.10;
    public static final double LONG_DELVE_HUNGER_REDUCTION_PER_STACK = 0.25;
    public static final int[] LONG_DELVE_STACK_CAPS = {2, 4};

    /**
     * Seconds since the last player-broken block after which time-based bonuses stop
     * accruing. Long Delve uses 60; Silent Stone uses {@link #SILENT_STONE_IDLE_SECONDS}.
     *
     * <p>Without a guard, every node that pays for time pays for standing still.</p>
     */
    public static final int LONG_DELVE_IDLE_SECONDS = 60;

    /** Stonecutter's Patience: stone per +1 XP, and the per-tier maximum. */
    public static final int STONECUTTERS_PATIENCE_STONE_PER_XP = 10_000;
    public static final int[] STONECUTTERS_PATIENCE_MAX_XP = {3, 5};

    /** Kindred Rock: blocks of one ore type that attune it, and what attunement pays. */
    public static final int KINDRED_ROCK_THRESHOLD = 1_000;
    public static final double KINDRED_ROCK_RESONANCE_BONUS = 0.15;
    public static final double KINDRED_ROCK_BREAK_SPEED_BONUS = 0.20;

    /** Novice's Luck: bonus ore is {@code (LEVEL_CAP - teamLevel)%}, floored at zero. */
    public static final int NOVICES_LUCK_ZERO_AT_LEVEL = LEVEL_CAP;

    /** Apprentice's Ledger: ore blocks per trip that pay double. */
    public static final int APPRENTICES_LEDGER_ORE_COUNT = 100;

    /** Shallow Grace: bonus, and the depth record above which it still applies. */
    public static final double SHALLOW_GRACE_BONUS = 0.50;
    public static final int SHALLOW_GRACE_MAX_DEPTH_Y = 100;

    /** Guide Vein and Salvager's Eye: the team levels at which each retires. */
    public static final int GUIDE_VEIN_RETIRE_LEVEL = 15;
    public static final int SALVAGERS_EYE_RETIRE_LEVEL = 12;
    public static final double SALVAGERS_EYE_STONE_CHANCE = 0.05;

    /** Wanderer's Cache: newly explored chunks per cache. */
    public static final int WANDERERS_CACHE_CHUNKS_PER_DROP = 50;

    /** Frontier Bonus: bonus, and how long a chunk counts as fresh. */
    public static final double FRONTIER_BONUS = 0.40;
    public static final int FRONTIER_BONUS_FRESH_SECONDS = 600;

    /** Homeward Seam: cooldown between Vault-to-Vault teleports, in seconds. */
    public static final int HOMEWARD_SEAM_COOLDOWN_SECONDS = 300;

    /** Far Reach: how long a chunk stays loaded behind the player, per tier, in seconds. */
    public static final int[] FAR_REACH_TRAILING_SECONDS = {30, 120};

    /** Chapter's End: veins completed in one chunk before it fires. */
    public static final int CHAPTERS_END_VEINS_PER_CHUNK = 5;

    /** Clean Cut and Last Ore: the completion bonuses each pays. */
    public static final double CLEAN_CUT_BONUS = 0.50;
    public static final double LAST_ORE_MULTIPLIER = 2.0;

    /** Silent Stone: Resonance per second below Y=0, and its AFK guard. */
    public static final double SILENT_STONE_RESONANCE_PER_SECOND = 3.0;
    public static final int SILENT_STONE_IDLE_SECONDS = 10;

    /** Resonant Symbiosis: the together bonuses and the solo penalty. */
    public static final double SYMBIOSIS_RESONANCE_BONUS = 0.50;
    public static final double SYMBIOSIS_ORE_BONUS = 0.25;
    public static final double SYMBIOSIS_SOLO_PENALTY = 0.40;
    public static final int SYMBIOSIS_MEMBERS_REQUIRED = 2;

    /** Bedrock Communion: depth bonus per block, penalty band, and speed penalty. */
    public static final double BEDROCK_COMMUNION_PER_BLOCK = 0.02;
    public static final int BEDROCK_COMMUNION_PENALTY_MIN_Y = 32;
    public static final double BEDROCK_COMMUNION_SPEED_PENALTY = 0.50;

    /** Molten Seam: smelting radius around lava, and the Resonance bonus inside it. */
    public static final int MOLTEN_SEAM_RADIUS = 8;
    public static final double MOLTEN_SEAM_BONUS = 0.25;

    /** Brittle Stone: shatter chance, and how many adjacent ore blocks each break destroys. */
    public static final double BRITTLE_STONE_SHATTER_CHANCE = 0.10;
    public static final int BRITTLE_STONE_COLLATERAL_BLOCKS = 1;

    // =====================================================================
    // Effect magnitudes the node descriptions quote (§6.1, #148)
    // =====================================================================
    //
    // These were all prose before: "occasionally drops flint", "moderate geode
    // frequency", "a burst of vanilla XP", "1-3% (balance TBD)". A number a player
    // cannot read is a number the designer has not chosen, and the Tome had no
    // honest way to describe the node. Pinning them here means the tooltip, the
    // spec table and the implementation quote one source.

    /** Stone Memory: the per-tier side effects, beyond the flat XP. */
    public static final double STONE_MEMORY_FLINT_CHANCE = 0.10;
    public static final double STONE_MEMORY_NUGGET_CHANCE = 0.02;
    public static final double STONE_MEMORY_BURST_CHANCE = 0.005;

    /** Miner's Constitution: how long the tier 3 Regeneration I lasts, in seconds. */
    public static final int MINERS_CONSTITUTION_REGEN_SECONDS = 5;

    /** Deep Veins: ore density multiplier applied to the band just above bedrock. */
    public static final double[] DEEP_VEINS_DENSITY_MULTIPLIERS = {1.5, 2.0};
    public static final int DEEP_VEINS_BAND_BLOCKS = 30;

    /** Stone Reduction: fraction of the stone touching a vein converted to that vein's ore. */
    public static final double[] STONE_REDUCTION_CONVERSION = {0.10, 0.20};

    /**
     * Stratified: the two Y lines that sort ore by rarity.
     *
     * <p>Rare at or below {@link #STRATIFIED_RARE_MAX_Y}, uncommon up to
     * {@link #STRATIFIED_UNCOMMON_MAX_Y}, common above it to the top of the stone band.
     * Fixed rather than proportional so the answer to "how deep is the rare band" is the
     * same sentence in both dimension variants — expanding the Vault deepens the rare
     * band rather than moving every boundary.</p>
     */
    public static final int STRATIFIED_RARE_MAX_Y = 60;
    public static final int STRATIFIED_UNCOMMON_MAX_Y = 150;

    /** Geode Clusters: newly explored chunks per geode, per tier. Overworld is roughly 24. */
    public static final int[] GEODE_CLUSTERS_CHUNKS_PER_GEODE = {12, 6};
    public static final int GEODE_CLUSTERS_OVERWORLD_CHUNKS_PER_GEODE = 24;

    /** Ancient Traces: newly explored chunks per ancient debris below Y=0, per tier. */
    public static final int[] ANCIENT_TRACES_CHUNKS_PER_DEBRIS = {4, 2};

    /**
     * Volatile Veins: chance per ore break that the rest of the vein vanishes.
     *
     * <p>3% rather than the 2% first chosen, because Ultimine Safety subtracts a flat
     * 1% then 2% (§6.1) and a 2% base would let 3 skill points take the tradeoff to
     * exactly zero risk. A tradeoff that can be switched off by another node is not a
     * tradeoff. At 3% the floor is 1% and Ultimine Safety is still worth buying.</p>
     */
    public static final double VOLATILE_VEINS_DISAPPEAR_CHANCE = 0.03;

    /** Wanderer's Cache: chance a tier 2 cache also holds an enchanted book. */
    public static final double WANDERERS_CACHE_BOOK_CHANCE = 0.20;

    /** Pathfinder's Claim: vanilla XP for the first vein mined in a new chunk. */
    public static final int PATHFINDERS_CLAIM_XP = 50;

    /** Hoarder's Instinct: how close two resting orbs must be to merge into one cache. */
    public static final int HOARDERS_INSTINCT_MERGE_RADIUS = 4;

    // =====================================================================
    // Resonance tree — CLUSTER: Prospecting
    // =====================================================================

    public static final int[] VEIN_EXPANSION_COSTS = {1, 1, 2, 2, 3};
    public static final int[] VEIN_EXPANSION_LEVEL_REQS = {0, 2, 4, 7, 10};

    public static final int[] STONE_MEMORY_COSTS = {1, 1, 2, 2, 3};
    public static final int[] STONE_MEMORY_LEVEL_REQS = {0, 3, 6, 10, 14};

    public static final int[] GRAVEL_PURGE_COSTS = {1};
    public static final int[] GRAVEL_PURGE_LEVEL_REQS = {1};

    /** Renamed from {@code EFFICIENT_MINER_*}; tiers 4 and 5 also grant max health. */
    public static final int[] MINERS_CONSTITUTION_COSTS = {1, 1, 2, 2, 3};
    public static final int[] MINERS_CONSTITUTION_LEVEL_REQS = {0, 3, 6, 10, 15};

    /** Bonus max health granted inside the Vault by Miner's Constitution tiers 4 and 5. */
    public static final double[] MINERS_CONSTITUTION_BONUS_HEALTH = {0, 0, 0, 4.0, 8.0};

    public static final int[] SURE_FOOTING_COSTS = {1, 2};
    public static final int[] SURE_FOOTING_LEVEL_REQS = {2, 6};

    public static final int[] DEEP_BREATH_COSTS = {2, 4};
    public static final int[] DEEP_BREATH_LEVEL_REQS = {12, 18};

    /** Y below which Deep Breath applies. Above it the Vault is not trying to drown anyone. */
    public static final int DEEP_BREATH_MAX_Y = 32;

    public static final int[] NOVICES_LUCK_COSTS = {1};
    public static final int[] NOVICES_LUCK_LEVEL_REQS = {0};

    public static final int[] APPRENTICES_LEDGER_COSTS = {1};
    public static final int[] APPRENTICES_LEDGER_LEVEL_REQS = {0};

    public static final int[] SHALLOW_GRACE_COSTS = {1};
    public static final int[] SHALLOW_GRACE_LEVEL_REQS = {0};

    public static final int[] GUIDE_VEIN_COSTS = {1};
    public static final int[] GUIDE_VEIN_LEVEL_REQS = {0};

    public static final int[] SALVAGERS_EYE_COSTS = {1};
    public static final int[] SALVAGERS_EYE_LEVEL_REQS = {1};

    // =====================================================================
    // Resonance tree — CLUSTER: Excavation
    // =====================================================================

    public static final int[] VEIN_PROLIFERATION_COSTS = {1, 1, 2, 2, 3};
    public static final int[] VEIN_PROLIFERATION_LEVEL_REQS = {2, 4, 7, 10, 14};

    public static final int[] DEEP_VEINS_COSTS = {2, 3};
    public static final int[] DEEP_VEINS_LEVEL_REQS = {5, 9};

    public static final int[] STONE_REDUCTION_COSTS = {1, 2};
    public static final int[] STONE_REDUCTION_LEVEL_REQS = {3, 7};

    public static final int[] VEIN_SHAPING_COSTS = {2, 3};
    public static final int[] VEIN_SHAPING_LEVEL_REQS = {4, 8};

    /** The three Vein Shape options. Free by definition; the level matches the parent's tier 1. */
    public static final int[] VEIN_SHAPE_OPTION_COSTS = {0};
    public static final int[] VEIN_SHAPE_OPTION_LEVEL_REQS = {4};

    public static final int[] DEEP_HABIT_COSTS = {2};
    public static final int[] DEEP_HABIT_LEVEL_REQS = {7};

    public static final int[] LONG_DELVE_COSTS = {2, 3};
    public static final int[] LONG_DELVE_LEVEL_REQS = {7, 12};

    public static final int[] VOLATILE_VEINS_COSTS = {2};
    public static final int[] VOLATILE_VEINS_LEVEL_REQS = {6};

    public static final int[] MOLTEN_SEAM_COSTS = {2};
    public static final int[] MOLTEN_SEAM_LEVEL_REQS = {8};

    public static final int[] GREEDY_SEAMS_COSTS = {4};
    public static final int[] GREEDY_SEAMS_LEVEL_REQS = {6};

    public static final int[] RESONANT_OVERLOAD_COSTS = {4};
    public static final int[] RESONANT_OVERLOAD_LEVEL_REQS = {6};

    // =====================================================================
    // Resonance tree — CLUSTER: Assay
    // =====================================================================

    public static final int[] ORE_ATTUNEMENT_COSTS = {1, 2, 2};
    public static final int[] ORE_ATTUNEMENT_LEVEL_REQS = {3, 6, 10};

    /** The three Ore Focus options, replacing the old stacking Common/Uncommon/Rare chain. */
    public static final int[] ORE_FOCUS_OPTION_COSTS = {0};
    public static final int[] ORE_FOCUS_OPTION_LEVEL_REQS = {3};

    public static final int[] GEODE_CLUSTERS_COSTS = {1, 2};
    public static final int[] GEODE_CLUSTERS_LEVEL_REQS = {4, 8};

    public static final int[] ANCIENT_TRACES_COSTS = {5, 5};
    public static final int[] ANCIENT_TRACES_LEVEL_REQS = {21, 26};

    public static final int[] ANCIENT_KNOWLEDGE_COSTS = {1, 1, 2};
    public static final int[] ANCIENT_KNOWLEDGE_LEVEL_REQS = {2, 5, 9};

    public static final int[] STONECUTTERS_PATIENCE_COSTS = {2, 3};
    public static final int[] STONECUTTERS_PATIENCE_LEVEL_REQS = {6, 11};

    public static final int[] CALLOUSED_HANDS_COSTS = {2, 3, 4};
    public static final int[] CALLOUSED_HANDS_LEVEL_REQS = {6, 12, 18};

    public static final int[] VEIN_SIGHT_COSTS = {2};
    public static final int[] VEIN_SIGHT_LEVEL_REQS = {5};

    /** Seconds a vein stays outlined by Vein Sight after its first block breaks. */
    public static final int VEIN_SIGHT_DURATION_SECONDS = 30;

    public static final int[] STONECALLER_COSTS = {3};
    public static final int[] STONECALLER_LEVEL_REQS = {12};

    public static final int[] PROSPECTORS_EYE_COSTS = {3};
    public static final int[] PROSPECTORS_EYE_LEVEL_REQS = {8};

    public static final int[] STONE_CURSE_COSTS = {2};
    public static final int[] STONE_CURSE_LEVEL_REQS = {4};

    // =====================================================================
    // Resonance tree — CLUSTER: Metallurgy
    // =====================================================================

    /** Renamed from {@code ORE_SENSE_*}; the name Ore Sense now belongs to Prospector's Eye. */
    public static final int[] VEIN_FORTUNE_COSTS = {2, 3, 4};
    public static final int[] VEIN_FORTUNE_LEVEL_REQS = {5, 9, 13};

    /** Tiers 4-6 exist only with Ore Doubling chosen and Mekanism loaded (§6.1). */
    public static final int[] ORE_WORKING_COSTS = {2, 2, 3, 7, 10, 15};
    public static final int[] ORE_WORKING_LEVEL_REQS = {8, 12, 16, 20, 25, 30};

    public static final int[] YIELD_OPTION_COSTS = {0};
    public static final int[] YIELD_OPTION_LEVEL_REQS = {8};

    public static final int[] RUNIC_ATTUNEMENT_COSTS = {3, 3, 4};
    public static final int[] RUNIC_ATTUNEMENT_LEVEL_REQS = {10, 14, 17};

    public static final int[] KINDRED_ROCK_COSTS = {3, 3};
    public static final int[] KINDRED_ROCK_LEVEL_REQS = {10, 15};

    public static final int[] HIGHWATER_MARK_COSTS = {2, 3};
    public static final int[] HIGHWATER_MARK_LEVEL_REQS = {9, 14};

    public static final int[] BRITTLE_STONE_COSTS = {5};
    public static final int[] BRITTLE_STONE_LEVEL_REQS = {13};

    public static final int[] VAULT_FEVER_COSTS = {2};
    public static final int[] VAULT_FEVER_LEVEL_REQS = {7};

    // =====================================================================
    // Resonance tree — CLUSTER: Claim
    // =====================================================================

    public static final int[] VAULT_PRESENCE_COSTS = {2, 2, 3};
    public static final int[] VAULT_PRESENCE_LEVEL_REQS = {5, 9, 14};

    public static final int[] AUTOMATED_EXTRACTION_COSTS = {2, 3};
    public static final int[] AUTOMATED_EXTRACTION_LEVEL_REQS = {8, 12};

    public static final int[] FAR_REACH_COSTS = {2, 3};
    public static final int[] FAR_REACH_LEVEL_REQS = {12, 16};

    /** Tiers 2 and 3 are the absorbed Cartographer's Instinct map (§6.1). */
    public static final int[] SEISMIC_SENSE_COSTS = {2, 2, 3};
    public static final int[] SEISMIC_SENSE_LEVEL_REQS = {6, 11, 15};

    public static final int[] WANDERERS_CACHE_COSTS = {2, 3};
    public static final int[] WANDERERS_CACHE_LEVEL_REQS = {6, 12};

    public static final int[] FRONTIER_BONUS_COSTS = {2};
    public static final int[] FRONTIER_BONUS_LEVEL_REQS = {8};

    public static final int[] PATHFINDERS_CLAIM_COSTS = {2};
    public static final int[] PATHFINDERS_CLAIM_LEVEL_REQS = {7};

    public static final int[] HOMEWARD_SEAM_COSTS = {3};
    public static final int[] HOMEWARD_SEAM_LEVEL_REQS = {10};

    public static final int[] VAULT_KEEPS_IT_COSTS = {3};
    public static final int[] VAULT_KEEPS_IT_LEVEL_REQS = {10};

    public static final int[] SECOND_WIND_COSTS = {4};
    public static final int[] SECOND_WIND_LEVEL_REQS = {14};

    public static final int[] SECOND_WIND_HOMEWARD_COSTS = {8};
    public static final int[] SECOND_WIND_HOMEWARD_LEVEL_REQS = {20};

    public static final int[] VAULTS_BLESSING_COSTS = {3};
    public static final int[] VAULTS_BLESSING_LEVEL_REQS = {8};

    public static final int[] VAULTS_PURITY_COSTS = {3};
    public static final int[] VAULTS_PURITY_LEVEL_REQS = {8};

    public static final int[] TITHE_COSTS = {2};
    public static final int[] TITHE_LEVEL_REQS = {5};

    // =====================================================================
    // Resonance tree — CLUSTER: Deep Lore
    // =====================================================================

    public static final int[] VAULT_ECHO_COSTS = {1, 1, 2};
    public static final int[] VAULT_ECHO_LEVEL_REQS = {3, 6, 9};

    public static final int[] ECHO_CHAMBER_COSTS = {3};
    public static final int[] ECHO_CHAMBER_LEVEL_REQS = {11};

    public static final int[] TWIN_VEINS_COSTS = {2, 2, 3};
    public static final int[] TWIN_VEINS_LEVEL_REQS = {6, 10, 14};

    public static final int[] LAST_ORE_COSTS = {3};
    public static final int[] LAST_ORE_LEVEL_REQS = {10};

    public static final int[] CLEAN_CUT_COSTS = {3};
    public static final int[] CLEAN_CUT_LEVEL_REQS = {13};

    public static final int[] VEIN_DISCIPLINE_COSTS = {3};
    public static final int[] VEIN_DISCIPLINE_LEVEL_REQS = {12};

    public static final int[] CHAPTERS_END_COSTS = {4};
    public static final int[] CHAPTERS_END_LEVEL_REQS = {16};

    public static final int[] DEEP_HARVEST_COSTS = {4};
    public static final int[] DEEP_HARVEST_LEVEL_REQS = {20};

    public static final int[] RESONANT_DRAW_COSTS = {1, 1, 2};
    public static final int[] RESONANT_DRAW_LEVEL_REQS = {5, 9, 13};

    public static final int[] ORB_COLLECTION_OPTION_COSTS = {0};
    public static final int[] ORB_COLLECTION_OPTION_LEVEL_REQS = {5};

    // =====================================================================
    // Resonance tree — CLUSTER: Broad Cut (hidden when Ultimine absent)
    // =====================================================================

    public static final int[] ULTIMINE_EXPANSION_COSTS = {1, 2, 3};
    public static final int[] ULTIMINE_EXPANSION_LEVEL_REQS = {3, 7, 12};

    public static final int[] ULTIMINE_SAFETY_COSTS = {1, 2};
    public static final int[] ULTIMINE_SAFETY_LEVEL_REQS = {5, 9};

    public static final int[] ULTIMINE_GAMBIT_COSTS = {2};
    public static final int[] ULTIMINE_GAMBIT_LEVEL_REQS = {9};

    // =====================================================================
    // Resonance tree — CLUSTER: Mastery (keystones only)
    // =====================================================================

    public static final int[] VAULT_EXPANSION_COSTS = {10};
    public static final int[] VAULT_EXPANSION_LEVEL_REQS = {18};

    public static final int[] FULL_SPECTRUM_COSTS = {8};
    public static final int[] FULL_SPECTRUM_LEVEL_REQS = {22};

    public static final int[] SILENT_STONE_COSTS = {8};
    public static final int[] SILENT_STONE_LEVEL_REQS = {24};

    public static final int[] RESONANT_SYMBIOSIS_COSTS = {8};
    public static final int[] RESONANT_SYMBIOSIS_LEVEL_REQS = {20};

    public static final int[] BEDROCK_COMMUNION_COSTS = {10};
    public static final int[] BEDROCK_COMMUNION_LEVEL_REQS = {26};

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
