package com.orevault.orevault.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;

import static com.orevault.orevault.skill.NodeCosts.*;

/**
 * Immutable registry of every skill-tree node defined in §6 of the design spec.
 *
 * <p>Built once at class-load from the constants in {@link NodeCosts}. Lookups are
 * by stable id; iteration order is deterministic (registration order), and that order
 * is the §6.1 cluster order, so a reader of the spec and a reader of this file see the
 * same tree.</p>
 *
 * <h2>What is not in here</h2>
 *
 * <p><b>Cluster anchors.</b> They are not nodes: they cannot be bought, cost nothing,
 * and carry one number each. That lives on {@link Cluster}, and {@link #anchorGate}
 * turns its fraction into a point total. Registering them as zero-cost nodes would
 * mean every purchase path had to special-case "except this one".</p>
 *
 * <p><b>Node effects.</b> Costs, prerequisites and structure only. What a node does
 * lives with the system it affects.</p>
 */
public final class NodeDefs {

    private static final Map<String, NodeDef> REGISTRY = buildRegistry();
    private static final List<NodeDef> ALL = List.copyOf(REGISTRY.values());
    private static final Map<Tree, List<NodeDef>> BY_TREE = indexByTree();
    private static final Map<Cluster, List<NodeDef>> BY_CLUSTER = indexByCluster();
    private static final Map<Tree, Integer> TOTAL_COST = totalCosts();

    private NodeDefs() {
    }

    public static @Nullable NodeDef get(String id) {
        return REGISTRY.get(id);
    }

    /** All nodes in deterministic registration order. */
    public static List<NodeDef> all() {
        return ALL;
    }

    /** Nodes belonging to the given tree, in registration order. */
    public static List<NodeDef> getByTree(Tree tree) {
        return BY_TREE.get(tree);
    }

    /** Nodes belonging to the given cluster, in registration order. */
    public static List<NodeDef> getByCluster(Cluster cluster) {
        return BY_CLUSTER.get(cluster);
    }

    /** Total skill-point cost of fully purchasing every node in the given tree. */
    public static int totalTreeCost(Tree tree) {
        return TOTAL_COST.get(tree);
    }

    /**
     * Skill points that must be spent in the Resonance tree before {@code cluster} opens (§6.1).
     *
     * <p>Derived from {@link Cluster#gateFraction()} and the tree's real total cost rather than
     * hard-coded, because the total moves every time a node is added and absolute gates silently
     * stop meaning what they were chosen to mean. Rounded up, so a fraction of a point never
     * opens a cluster early.</p>
     */
    public static int anchorGate(Cluster cluster) {
        if (cluster == Cluster.ANIMUS_ZONES || cluster == Cluster.ANIMUS_REWARDS) {
            return 0;
        }
        return (int) Math.ceil(cluster.gateFraction() * totalTreeCost(Tree.RESONANCE));
    }

    /** The fork options that specialize {@code parentId}, in registration order. */
    public static List<NodeDef> forkOptions(String parentId) {
        List<NodeDef> out = new ArrayList<>();
        for (NodeDef def : ALL) {
            if (parentId.equals(def.forkParentId())) {
                out.add(def);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static Map<Tree, List<NodeDef>> indexByTree() {
        Map<Tree, List<NodeDef>> map = new EnumMap<>(Tree.class);
        for (Tree tree : Tree.values()) {
            List<NodeDef> nodes = new ArrayList<>();
            for (NodeDef def : ALL) {
                if (def.tree() == tree) {
                    nodes.add(def);
                }
            }
            map.put(tree, Collections.unmodifiableList(nodes));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Cluster, List<NodeDef>> indexByCluster() {
        Map<Cluster, List<NodeDef>> map = new EnumMap<>(Cluster.class);
        for (Cluster cluster : Cluster.values()) {
            List<NodeDef> nodes = new ArrayList<>();
            for (NodeDef def : ALL) {
                if (def.cluster() == cluster) {
                    nodes.add(def);
                }
            }
            map.put(cluster, Collections.unmodifiableList(nodes));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Tree, Integer> totalCosts() {
        Map<Tree, Integer> map = new EnumMap<>(Tree.class);
        for (Tree tree : Tree.values()) {
            int total = 0;
            for (NodeDef def : BY_TREE.get(tree)) {
                for (int cost : def.costs()) {
                    total += cost;
                }
            }
            map.put(tree, total);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, NodeDef> buildRegistry() {
        Map<String, NodeDef> map = new LinkedHashMap<>();

        // =================================================================
        // Resonance — CLUSTER: Prospecting (anchor: 0% of the tree)
        // =================================================================
        small(map, "vein_expansion", "Vein Expansion", Cluster.PROSPECTING,
                VEIN_EXPANSION_COSTS, VEIN_EXPANSION_LEVEL_REQS, List.of());
        small(map, "stone_memory", "Stone Memory", Cluster.PROSPECTING,
                STONE_MEMORY_COSTS, STONE_MEMORY_LEVEL_REQS, List.of());
        small(map, "gravel_purge", "Gravel Purge", Cluster.PROSPECTING,
                GRAVEL_PURGE_COSTS, GRAVEL_PURGE_LEVEL_REQS, List.of());
        // Renamed from efficient_miner; see NodeMigration. The Hunger branch is gone.
        small(map, "miners_constitution", "Miner's Constitution", Cluster.PROSPECTING,
                MINERS_CONSTITUTION_COSTS, MINERS_CONSTITUTION_LEVEL_REQS, List.of());
        small(map, "sure_footing", "Sure Footing", Cluster.PROSPECTING,
                SURE_FOOTING_COSTS, SURE_FOOTING_LEVEL_REQS, List.of());
        small(map, "deep_breath", "Deep Breath", Cluster.PROSPECTING,
                DEEP_BREATH_COSTS, DEEP_BREATH_LEVEL_REQS, List.of(pre("miners_constitution", 3)));
        growth(map, "novices_luck", "Novice's Luck", Cluster.PROSPECTING,
                NOVICES_LUCK_COSTS, NOVICES_LUCK_LEVEL_REQS, List.of());
        growth(map, "apprentices_ledger", "Apprentice's Ledger", Cluster.PROSPECTING,
                APPRENTICES_LEDGER_COSTS, APPRENTICES_LEDGER_LEVEL_REQS, List.of());
        growth(map, "shallow_grace", "Shallow Grace", Cluster.PROSPECTING,
                SHALLOW_GRACE_COSTS, SHALLOW_GRACE_LEVEL_REQS, List.of());
        growth(map, "guide_vein", "Guide Vein", Cluster.PROSPECTING,
                GUIDE_VEIN_COSTS, GUIDE_VEIN_LEVEL_REQS, List.of());
        growth(map, "salvagers_eye", "Salvager's Eye", Cluster.PROSPECTING,
                SALVAGERS_EYE_COSTS, SALVAGERS_EYE_LEVEL_REQS, List.of());

        // =================================================================
        // Resonance — CLUSTER: Excavation
        // =================================================================
        small(map, "vein_proliferation", "Vein Proliferation", Cluster.EXCAVATION,
                VEIN_PROLIFERATION_COSTS, VEIN_PROLIFERATION_LEVEL_REQS, List.of(pre("vein_expansion", 1)));
        small(map, "deep_veins", "Deep Veins", Cluster.EXCAVATION,
                DEEP_VEINS_COSTS, DEEP_VEINS_LEVEL_REQS, List.of(pre("vein_proliferation", 2)));
        small(map, "stone_reduction", "Stone Reduction", Cluster.EXCAVATION,
                STONE_REDUCTION_COSTS, STONE_REDUCTION_LEVEL_REQS, List.of(pre("gravel_purge", 1)));
        forkParent(map, "vein_shaping", "Vein Shaping", Cluster.EXCAVATION, "vein_shape",
                VEIN_SHAPING_COSTS, VEIN_SHAPING_LEVEL_REQS, List.of(pre("vein_proliferation", 2)));
        forkOption(map, "abundance", "Abundance", Cluster.EXCAVATION, "vein_shape", "vein_shaping",
                VEIN_SHAPE_OPTION_LEVEL_REQS);
        forkOption(map, "vein_singularity", "Vein Singularity", Cluster.EXCAVATION, "vein_shape", "vein_shaping",
                VEIN_SHAPE_OPTION_LEVEL_REQS);
        forkOption(map, "stratified", "Stratified", Cluster.EXCAVATION, "vein_shape", "vein_shaping",
                VEIN_SHAPE_OPTION_LEVEL_REQS);
        small(map, "deep_habit", "Deep Habit", Cluster.EXCAVATION,
                DEEP_HABIT_COSTS, DEEP_HABIT_LEVEL_REQS, List.of(pre("vein_proliferation", 1)));
        small(map, "long_delve", "Long Delve", Cluster.EXCAVATION,
                LONG_DELVE_COSTS, LONG_DELVE_LEVEL_REQS, List.of(pre("deep_habit", 1)));
        tradeoff(map, "volatile_veins", "Volatile Veins", Cluster.EXCAVATION,
                VOLATILE_VEINS_COSTS, VOLATILE_VEINS_LEVEL_REQS, List.of(pre("vein_expansion", 2)));
        tradeoff(map, "molten_seam", "Molten Seam", Cluster.EXCAVATION,
                MOLTEN_SEAM_COSTS, MOLTEN_SEAM_LEVEL_REQS, List.of(pre("deep_veins", 1)));
        pact(map, "greedy_seams", "Greedy Seams", Cluster.EXCAVATION,
                GREEDY_SEAMS_COSTS, GREEDY_SEAMS_LEVEL_REQS, List.of(pre("vein_expansion", 1)),
                "resonant_overload");
        pact(map, "resonant_overload", "Resonant Overload", Cluster.EXCAVATION,
                RESONANT_OVERLOAD_COSTS, RESONANT_OVERLOAD_LEVEL_REQS, List.of(pre("vein_expansion", 1)),
                "greedy_seams");

        // =================================================================
        // Resonance — CLUSTER: Assay
        // =================================================================
        forkParent(map, "ore_attunement", "Ore Attunement", Cluster.ASSAY, "ore_focus",
                ORE_ATTUNEMENT_COSTS, ORE_ATTUNEMENT_LEVEL_REQS, List.of());
        forkOption(map, "common_focus", "Common Focus", Cluster.ASSAY, "ore_focus", "ore_attunement",
                ORE_FOCUS_OPTION_LEVEL_REQS);
        forkOption(map, "uncommon_focus", "Uncommon Focus", Cluster.ASSAY, "ore_focus", "ore_attunement",
                ORE_FOCUS_OPTION_LEVEL_REQS);
        forkOption(map, "rare_focus", "Rare Focus", Cluster.ASSAY, "ore_focus", "ore_attunement",
                ORE_FOCUS_OPTION_LEVEL_REQS);
        small(map, "geode_clusters", "Geode Clusters", Cluster.ASSAY,
                GEODE_CLUSTERS_COSTS, GEODE_CLUSTERS_LEVEL_REQS, List.of(pre("stone_reduction", 1)));
        // §6.1 words this as "Rare Focus T2 or Full Spectrum, and Vault Expansion". A prereq list is
        // an AND of node/tier pairs and cannot express the OR, and a fork option has no tier 2 to
        // require; the Ore Attunement tier is the paid part of "Rare Focus T2". Whether the chosen
        // focus is actually Rare is checked where the node takes effect, not where it is bought.
        small(map, "ancient_traces", "Ancient Traces", Cluster.ASSAY,
                ANCIENT_TRACES_COSTS, ANCIENT_TRACES_LEVEL_REQS,
                List.of(pre("ore_attunement", 2), pre("vault_expansion", 1)));
        small(map, "ancient_knowledge", "Ancient Knowledge", Cluster.ASSAY,
                ANCIENT_KNOWLEDGE_COSTS, ANCIENT_KNOWLEDGE_LEVEL_REQS, List.of(pre("stone_memory", 1)));
        small(map, "stonecutters_patience", "Stonecutter's Patience", Cluster.ASSAY,
                STONECUTTERS_PATIENCE_COSTS, STONECUTTERS_PATIENCE_LEVEL_REQS, List.of(pre("stone_memory", 2)));
        small(map, "calloused_hands", "Calloused Hands", Cluster.ASSAY,
                CALLOUSED_HANDS_COSTS, CALLOUSED_HANDS_LEVEL_REQS, List.of(pre("stone_memory", 2)));
        notable(map, "vein_sight", "Vein Sight", Cluster.ASSAY,
                VEIN_SIGHT_COSTS, VEIN_SIGHT_LEVEL_REQS, List.of());
        notable(map, "stonecaller", "Stonecaller", Cluster.ASSAY,
                STONECALLER_COSTS, STONECALLER_LEVEL_REQS, List.of(pre("stone_memory", 4)));
        notable(map, "prospectors_eye", "Prospector's Eye", Cluster.ASSAY,
                PROSPECTORS_EYE_COSTS, PROSPECTORS_EYE_LEVEL_REQS, List.of(pre("vein_fortune", 1)));
        tradeoff(map, "stone_curse", "Stone Curse", Cluster.ASSAY,
                STONE_CURSE_COSTS, STONE_CURSE_LEVEL_REQS, List.of(pre("stone_memory", 2)));

        // =================================================================
        // Resonance — CLUSTER: Metallurgy
        // =================================================================
        // Renamed from ore_sense; see NodeMigration.
        small(map, "vein_fortune", "Vein Fortune", Cluster.METALLURGY,
                VEIN_FORTUNE_COSTS, VEIN_FORTUNE_LEVEL_REQS, List.of(pre("vein_proliferation", 1)));
        forkParent(map, "ore_working", "Ore Working", Cluster.METALLURGY, "yield",
                ORE_WORKING_COSTS, ORE_WORKING_LEVEL_REQS, List.of(pre("vein_fortune", 1)));
        forkOption(map, "ore_doubling", "Ore Doubling", Cluster.METALLURGY, "yield", "ore_working",
                YIELD_OPTION_LEVEL_REQS);
        forkOption(map, "smelters_intuition", "Smelter's Intuition", Cluster.METALLURGY, "yield", "ore_working",
                YIELD_OPTION_LEVEL_REQS);
        small(map, "runic_attunement", "Runic Attunement", Cluster.METALLURGY,
                RUNIC_ATTUNEMENT_COSTS, RUNIC_ATTUNEMENT_LEVEL_REQS, List.of(pre("ore_working", 1)));
        small(map, "kindred_rock", "Kindred Rock", Cluster.METALLURGY,
                KINDRED_ROCK_COSTS, KINDRED_ROCK_LEVEL_REQS, List.of(pre("vein_fortune", 1)));
        small(map, "highwater_mark", "Highwater Mark", Cluster.METALLURGY,
                HIGHWATER_MARK_COSTS, HIGHWATER_MARK_LEVEL_REQS, List.of(pre("stone_memory", 3)));
        pact(map, "brittle_stone", "Brittle Stone", Cluster.METALLURGY,
                BRITTLE_STONE_COSTS, BRITTLE_STONE_LEVEL_REQS, List.of(pre("vein_fortune", 2)), null);
        tradeoff(map, "vault_fever", "Vault Fever", Cluster.METALLURGY,
                VAULT_FEVER_COSTS, VAULT_FEVER_LEVEL_REQS, List.of(pre("miners_constitution", 2)));

        // =================================================================
        // Resonance — CLUSTER: Claim
        // =================================================================
        small(map, "vault_presence", "Vault Presence", Cluster.CLAIM,
                VAULT_PRESENCE_COSTS, VAULT_PRESENCE_LEVEL_REQS, List.of());
        small(map, "automated_extraction", "Automated Extraction", Cluster.CLAIM,
                AUTOMATED_EXTRACTION_COSTS, AUTOMATED_EXTRACTION_LEVEL_REQS, List.of(pre("vault_presence", 1)));
        small(map, "far_reach", "Far Reach", Cluster.CLAIM,
                FAR_REACH_COSTS, FAR_REACH_LEVEL_REQS, List.of(pre("automated_extraction", 1)));
        notable(map, "seismic_sense", "Seismic Sense", Cluster.CLAIM,
                SEISMIC_SENSE_COSTS, SEISMIC_SENSE_LEVEL_REQS, List.of(pre("vault_presence", 1)));
        small(map, "wanderers_cache", "Wanderer's Cache", Cluster.CLAIM,
                WANDERERS_CACHE_COSTS, WANDERERS_CACHE_LEVEL_REQS, List.of());
        small(map, "frontier_bonus", "Frontier Bonus", Cluster.CLAIM,
                FRONTIER_BONUS_COSTS, FRONTIER_BONUS_LEVEL_REQS, List.of());
        small(map, "pathfinders_claim", "Pathfinder's Claim", Cluster.CLAIM,
                PATHFINDERS_CLAIM_COSTS, PATHFINDERS_CLAIM_LEVEL_REQS, List.of());
        notable(map, "homeward_seam", "Homeward Seam", Cluster.CLAIM,
                HOMEWARD_SEAM_COSTS, HOMEWARD_SEAM_LEVEL_REQS, List.of(pre("vault_presence", 1)));
        small(map, "vault_keeps_it", "The Vault Keeps It", Cluster.CLAIM,
                VAULT_KEEPS_IT_COSTS, VAULT_KEEPS_IT_LEVEL_REQS, List.of());
        small(map, "second_wind", "Second Wind", Cluster.CLAIM,
                SECOND_WIND_COSTS, SECOND_WIND_LEVEL_REQS, List.of(pre("miners_constitution", 3)));
        tradeoff(map, "second_wind_homeward", "Second Wind: Homeward", Cluster.CLAIM,
                SECOND_WIND_HOMEWARD_COSTS, SECOND_WIND_HOMEWARD_LEVEL_REQS, List.of(pre("second_wind", 1)));
        exclusivePair(map, "vaults_blessing", "Vault's Blessing", Cluster.CLAIM,
                VAULTS_BLESSING_COSTS, VAULTS_BLESSING_LEVEL_REQS, "vaults_purity");
        exclusivePair(map, "vaults_purity", "Vault's Purity", Cluster.CLAIM,
                VAULTS_PURITY_COSTS, VAULTS_PURITY_LEVEL_REQS, "vaults_blessing");
        tradeoff(map, "tithe", "Tithe", Cluster.CLAIM,
                TITHE_COSTS, TITHE_LEVEL_REQS, List.of());

        // =================================================================
        // Resonance — CLUSTER: Deep Lore
        // =================================================================
        small(map, "vault_echo", "Vault Echo", Cluster.DEEP_LORE,
                VAULT_ECHO_COSTS, VAULT_ECHO_LEVEL_REQS, List.of(pre("vein_expansion", 2)));
        notable(map, "echo_chamber", "Echo Chamber", Cluster.DEEP_LORE,
                ECHO_CHAMBER_COSTS, ECHO_CHAMBER_LEVEL_REQS, List.of(pre("vault_echo", 3)));
        small(map, "twin_veins", "Twin Veins", Cluster.DEEP_LORE,
                TWIN_VEINS_COSTS, TWIN_VEINS_LEVEL_REQS, List.of(pre("vein_expansion", 3)));
        small(map, "last_ore", "Last Ore", Cluster.DEEP_LORE,
                LAST_ORE_COSTS, LAST_ORE_LEVEL_REQS, List.of(pre("vault_echo", 1)));
        small(map, "clean_cut", "Clean Cut", Cluster.DEEP_LORE,
                CLEAN_CUT_COSTS, CLEAN_CUT_LEVEL_REQS, List.of(pre("vault_echo", 2)));
        small(map, "vein_discipline", "Vein Discipline", Cluster.DEEP_LORE,
                VEIN_DISCIPLINE_COSTS, VEIN_DISCIPLINE_LEVEL_REQS, List.of(pre("vault_echo", 2)));
        notable(map, "chapters_end", "Chapter's End", Cluster.DEEP_LORE,
                CHAPTERS_END_COSTS, CHAPTERS_END_LEVEL_REQS, List.of(pre("twin_veins", 2)));
        notable(map, "deep_harvest", "Deep Harvest", Cluster.DEEP_LORE,
                DEEP_HARVEST_COSTS, DEEP_HARVEST_LEVEL_REQS,
                List.of(pre("deep_veins", 2), pre("vault_expansion", 1)));
        forkParent(map, "resonant_draw", "Resonant Draw", Cluster.DEEP_LORE, "orb_collection",
                RESONANT_DRAW_COSTS, RESONANT_DRAW_LEVEL_REQS, List.of());
        forkOption(map, "resonance_magnetism", "Resonance Magnetism", Cluster.DEEP_LORE,
                "orb_collection", "resonant_draw", ORB_COLLECTION_OPTION_LEVEL_REQS);
        forkOption(map, "hoarders_instinct", "Hoarder's Instinct", Cluster.DEEP_LORE,
                "orb_collection", "resonant_draw", ORB_COLLECTION_OPTION_LEVEL_REQS);

        // =================================================================
        // Resonance — CLUSTER: Broad Cut (absent without FTB Ultimine)
        // =================================================================
        ultimine(map, "ultimine_expansion", "Ultimine Expansion", NodeClass.SMALL,
                ULTIMINE_EXPANSION_COSTS, ULTIMINE_EXPANSION_LEVEL_REQS, List.of());
        ultimine(map, "ultimine_safety", "Ultimine Safety", NodeClass.SMALL,
                ULTIMINE_SAFETY_COSTS, ULTIMINE_SAFETY_LEVEL_REQS, List.of(pre("ultimine_expansion", 1)));
        ultimine(map, "ultimine_gambit", "Volatile Veins: Ultimine Gambit", NodeClass.TRADEOFF,
                ULTIMINE_GAMBIT_COSTS, ULTIMINE_GAMBIT_LEVEL_REQS,
                List.of(pre("volatile_veins", 1), pre("ultimine_expansion", 1)));

        // =================================================================
        // Resonance — CLUSTER: Mastery (keystones only)
        // =================================================================
        keystone(map, "vault_expansion", "Vault Expansion",
                VAULT_EXPANSION_COSTS, VAULT_EXPANSION_LEVEL_REQS,
                List.of(pre("ore_attunement", 3), pre("vein_expansion", 5), pre("miners_constitution", 4)));
        keystone(map, "full_spectrum", "Full Spectrum",
                FULL_SPECTRUM_COSTS, FULL_SPECTRUM_LEVEL_REQS, List.of(pre("ore_attunement", 3)));
        keystone(map, "silent_stone", "Silent Stone",
                SILENT_STONE_COSTS, SILENT_STONE_LEVEL_REQS, List.of(pre("vault_expansion", 1)));
        keystone(map, "resonant_symbiosis", "Resonant Symbiosis",
                RESONANT_SYMBIOSIS_COSTS, RESONANT_SYMBIOSIS_LEVEL_REQS, List.of());
        keystone(map, "bedrock_communion", "Bedrock Communion",
                BEDROCK_COMMUNION_COSTS, BEDROCK_COMMUNION_LEVEL_REQS,
                List.of(pre("vault_expansion", 1), pre("deep_harvest", 1)));

        // =================================================================
        // Animus tree (§6.2) — deferred to the post-1.0 epic (#90), kept registered
        // so the tree, the level curve and the Tome's second tab have data to read.
        // =================================================================
        animus(map, "zone_frequency", "Zone Frequency", Cluster.ANIMUS_ZONES,
                ZONE_FREQUENCY_COSTS, ZONE_FREQUENCY_LEVEL_REQS, List.of());
        animus(map, "zone_pack_size", "Zone Pack Size", Cluster.ANIMUS_ZONES,
                ZONE_PACK_SIZE_COSTS, ZONE_PACK_SIZE_LEVEL_REQS, List.of());
        animus(map, "zone_radius", "Zone Radius", Cluster.ANIMUS_ZONES,
                ZONE_RADIUS_COSTS, ZONE_RADIUS_LEVEL_REQS, List.of(pre("zone_frequency", 1)));
        animus(map, "mob_diversity", "Mob Diversity", Cluster.ANIMUS_ZONES,
                MOB_DIVERSITY_COSTS, MOB_DIVERSITY_LEVEL_REQS, List.of());
        animus(map, "reapers_claim", "Reaper's Claim", Cluster.ANIMUS_REWARDS,
                REAPERS_CLAIM_COSTS, REAPERS_CLAIM_LEVEL_REQS, List.of(pre("zone_frequency", 1)));
        animus(map, "corrupted_veins", "Corrupted Veins", Cluster.ANIMUS_REWARDS,
                CORRUPTED_VEINS_COSTS, CORRUPTED_VEINS_LEVEL_REQS, List.of(pre("zone_frequency", 2)));
        animus(map, "plunderers_share", "Plunderer's Share", Cluster.ANIMUS_REWARDS,
                PLUNDERERS_SHARE_COSTS, PLUNDERERS_SHARE_LEVEL_REQS, List.of());
        animus(map, "animus_amplifier", "Animus Amplifier", Cluster.ANIMUS_REWARDS,
                ANIMUS_AMPLIFIER_COSTS, ANIMUS_AMPLIFIER_LEVEL_REQS, List.of());
        animus(map, "soul_harvest", "Soul Harvest", Cluster.ANIMUS_REWARDS,
                SOUL_HARVEST_COSTS, SOUL_HARVEST_LEVEL_REQS,
                List.of(pre("reapers_claim", 3), pre("corrupted_veins", 2), pre("plunderers_share", 2)));

        return map;
    }

    private static Prereq pre(String nodeId, int minTier) {
        return new Prereq(nodeId, minTier);
    }

    // ----- registration helpers, one per node class -----

    private static void small(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                              int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.SMALL, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void notable(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                                int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.NOTABLE, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void growth(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                               int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.GROWTH, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void tradeoff(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                                 int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.TRADEOFF, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void pact(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                             int[] costs, int[] levelReqs, List<Prereq> prereqs,
                             @Nullable String exclusiveWith) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.PACT, null, null,
                costs, levelReqs, prereqs, exclusiveWith, false);
    }

    private static void keystone(Map<String, NodeDef> map, String id, String name,
                                 int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, Cluster.MASTERY, NodeClass.KEYSTONE, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void exclusivePair(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                                      int[] costs, int[] levelReqs, String exclusiveWith) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.SMALL, null, null,
                costs, levelReqs, List.of(), exclusiveWith, false);
    }

    private static void forkParent(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                                   String forkGroup, int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.FORK_PARENT, forkGroup, null,
                costs, levelReqs, prereqs, null, false);
    }

    /**
     * A fork option: 0 points, and its only prerequisite is its parent at tier 1.
     *
     * <p>The parent prereq is generated rather than passed in, because an option that does not
     * require its parent is the one malformed fork the type system cannot catch.</p>
     */
    private static void forkOption(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                                   String forkGroup, String parentId, int[] levelReqs) {
        node(map, id, name, Tree.RESONANCE, cluster, NodeClass.FORK_OPTION, forkGroup, parentId,
                new int[]{0}, levelReqs, List.of(pre(parentId, 1)), null, false);
    }

    private static void ultimine(Map<String, NodeDef> map, String id, String name, NodeClass nodeClass,
                                 int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.RESONANCE, Cluster.BROAD_CUT, nodeClass, null, null,
                costs, levelReqs, prereqs, null, true);
    }

    private static void animus(Map<String, NodeDef> map, String id, String name, Cluster cluster,
                               int[] costs, int[] levelReqs, List<Prereq> prereqs) {
        node(map, id, name, Tree.ANIMUS, cluster, NodeClass.SMALL, null, null,
                costs, levelReqs, prereqs, null, false);
    }

    private static void node(Map<String, NodeDef> map, String id, String name, Tree tree, Cluster cluster,
                             NodeClass nodeClass, @Nullable String forkGroup, @Nullable String forkParentId,
                             int[] costs, int[] levelReqs, List<Prereq> prereqs,
                             @Nullable String exclusiveWith, boolean ultimineOnly) {
        NodeDef def = new NodeDef(id, name, tree, cluster, nodeClass, forkGroup, forkParentId,
                costs, levelReqs, prereqs, exclusiveWith, ultimineOnly);
        if (map.put(id, def) != null) {
            throw new IllegalStateException("Duplicate node id: " + id);
        }
    }
}
