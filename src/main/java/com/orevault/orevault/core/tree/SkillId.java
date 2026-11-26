package com.orevault.orevault.core.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One constant per §6.1 skill, notable, keystone and travel stat: the ids that effects constants (#235, #211, #236), lang
 * keys ({@code node.orevault.<id>}) and saves are keyed by. The layout file is the one source of nodes (§6 Layout data);
 * this enum holds only the skill ids a node's {@code skill} field must name, and {@link TreeIds} checks the two agree.
 *
 * <p>An enum rather than string constants so effects tables can be {@code EnumMap}s checked complete by {@link #values()},
 * effect code can switch exhaustively, and a typo does not compile. A fork parent is an ordinary constant; it is a fork
 * parent because a {@link ForkOption} names it.
 *
 * <p>Each id is §6.1's heading lower-cased, apostrophes dropped and every other run of characters outside
 * {@code [a-z0-9]} one underscore, at most 64 characters (ADR 0006), with no exceptions. Ids are save data from the first
 * release (ADR 0005 section 6, "Registry ids are save data"): a rename needs a migration and the old id joins
 * {@link ReservedIds}.
 *
 * <p><b>Never persist or send {@link #ordinal()} or {@link #name()}.</b> Saves, the wire and lang keys use {@link #id()}
 * only, so reordering or renaming a Java constant changes no save and no packet.
 */
public enum SkillId {
    // Skills (§6.1 Skills), with their pacts, tradeoffs, fork parents and growth nodes.
    VEIN_EXPANSION("vein_expansion"),
    STONE_MEMORY("stone_memory"),
    GRAVEL_PURGE("gravel_purge"),
    MINERS_CONSTITUTION("miners_constitution"),
    SURE_FOOTING("sure_footing"),
    DEEP_BREATH("deep_breath"),
    NOVICES_LUCK("novices_luck"),
    APPRENTICES_LEDGER("apprentices_ledger"),
    SHALLOW_GRACE("shallow_grace"),
    GUIDE_VEIN("guide_vein"),
    SALVAGERS_EYE("salvagers_eye"),
    VEIN_PROLIFERATION("vein_proliferation"),
    DEEP_VEINS("deep_veins"),
    STONE_REDUCTION("stone_reduction"),
    VEIN_SHAPING("vein_shaping"),
    DEEP_HABIT("deep_habit"),
    LONG_DELVE("long_delve"),
    VOLATILE_VEINS("volatile_veins"),
    MOLTEN_SEAM("molten_seam"),
    GREEDY_SEAMS("greedy_seams"),
    RESONANT_OVERLOAD("resonant_overload"),
    ORE_ATTUNEMENT("ore_attunement"),
    GEODE_CLUSTERS("geode_clusters"),
    ANCIENT_TRACES("ancient_traces"),
    ANCIENT_KNOWLEDGE("ancient_knowledge"),
    STONECUTTERS_PATIENCE("stonecutters_patience"),
    CALLOUSED_HANDS("calloused_hands"),
    VEIN_SIGHT("vein_sight"),
    STONECALLER("stonecaller"),
    PROSPECTORS_EYE("prospectors_eye"),
    STONE_CURSE("stone_curse"),
    VEIN_FORTUNE("vein_fortune"),
    ORE_WORKING("ore_working"),
    RUNIC_ATTUNEMENT("runic_attunement"),
    KINDRED_ROCK("kindred_rock"),
    HIGHWATER_MARK("highwater_mark"),
    BRITTLE_STONE("brittle_stone"),
    DENSE_STONE("dense_stone"),
    VAULT_FEVER("vault_fever"),
    VAULT_PRESENCE("vault_presence"),
    AUTOMATED_EXTRACTION("automated_extraction"),
    FAR_REACH("far_reach"),
    SEISMIC_SENSE("seismic_sense"),
    WANDERERS_CACHE("wanderers_cache"),
    FRONTIER_BONUS("frontier_bonus"),
    PATHFINDERS_CLAIM("pathfinders_claim"),
    HOMEWARD_SEAM("homeward_seam"),
    THE_VAULT_KEEPS_IT("the_vault_keeps_it"),
    SECOND_WIND("second_wind"),
    SECOND_WIND_HOMEWARD("second_wind_homeward"),
    VAULTS_BLESSING("vaults_blessing"),
    VAULTS_PURITY("vaults_purity"),
    TITHE("tithe"),
    VAULT_ECHO("vault_echo"),
    ECHO_CHAMBER("echo_chamber"),
    TWIN_VEINS("twin_veins"),
    LAST_ORE("last_ore"),
    CLEAN_CUT("clean_cut"),
    VEIN_DISCIPLINE("vein_discipline"),
    CHAPTERS_END("chapters_end"),
    DEEP_HARVEST("deep_harvest"),
    RESONANT_DRAW("resonant_draw"),
    ULTIMINE_EXPANSION("ultimine_expansion"),
    ULTIMINE_SAFETY("ultimine_safety"),
    VOLATILE_VEINS_ULTIMINE_GAMBIT("volatile_veins_ultimine_gambit"),
    // Travel stats (§6.1 Travel stats): one skill each, its tier the count of its nodes owned.
    MINING_SPEED("mining_speed"),
    RESONANCE_GAIN("resonance_gain"),
    VANILLA_XP_FROM_ORE("vanilla_xp_from_ore"),
    MOVE_SPEED("move_speed"),
    BLOCK_REACH("block_reach"),
    MAX_HEALTH("max_health"),
    TOOL_CARE("tool_care"),
    TOOL_CHARGE("tool_charge"),
    SAFE_FALL("safe_fall"),
    ARMOUR("armour"),
    MASONS_REACH("masons_reach"),
    KIT_CARE("kit_care"),
    // Flavour notables (§6.1 Flavour notables).
    SPELUNKERS_STRIDE("spelunkers_stride"),
    LANDSCAPERS_SWING("landscapers_swing"),
    SEAM_COURIER("seam_courier"),
    KILN_TOUCH("kiln_touch"),
    WAYFARERS_LUNGS("wayfarers_lungs"),
    VAULT_FORGED("vault_forged"),
    RESONANT_DYNAMO("resonant_dynamo"),
    HAMMER_AND_PAXEL_AFFINITY("hammer_and_paxel_affinity"),
    HARVESTERS_LOT("harvesters_lot"),
    TEMPERED("tempered"),
    BOOKSEAM("bookseam"),
    STEADY_CHAIN("steady_chain"),
    DELVERS_LUNGS("delvers_lungs"),
    VAULT_SMITH("vault_smith"),
    ORE_IMPRINT("ore_imprint"),
    GEM_SEAM("gem_seam"),
    UNYIELDING_EDGE("unyielding_edge"),
    RESONANT_TRAIT("resonant_trait"),
    BLAST_WISE("blast_wise"),
    BUDDING_SEAM("budding_seam"),
    ASCENDING_EDGE("ascending_edge"),
    // Keystones (§6.1 Keystones).
    VAULT_EXPANSION("vault_expansion"),
    FULL_SPECTRUM("full_spectrum"),
    SILENT_STONE("silent_stone"),
    RESONANT_SYMBIOSIS("resonant_symbiosis"),
    BEDROCK_COMMUNION("bedrock_communion"),
    LONG_ARM("long_arm"),
    GLASS_EDGE("glass_edge"),
    GALVANIC_SEAM("galvanic_seam"),
    ECHOING_EDGE("echoing_edge");

    private static final Map<String, SkillId> BY_ID = new HashMap<>();

    static {
        for (SkillId s : values()) {
            BY_ID.put(s.id, s);
        }
    }

    private final String id;

    SkillId(String id) {
        this.id = id;
    }

    /** The saved, wired and lang-key form. */
    public String id() {
        return id;
    }

    /** The constant whose {@link #id()} is exactly {@code id}; empty for an unknown or reserved id. Reserved ids are
     * empty only because no constant carries one, which {@code SkillIdTest.noIdIsReservedOrStart} enforces. */
    public static Optional<SkillId> fromId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }
}
