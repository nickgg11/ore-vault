package com.orevault.orevault.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;

import static com.orevault.orevault.skill.NodeCosts.*;

/**
 * Immutable registry of every skill-tree node defined in §6 of the design spec.
 *
 * <p>Built once at class-load from the constants in {@link NodeCosts}. Lookups are
 * by stable id; iteration order is deterministic (registration order).</p>
 */
public final class NodeDefs {

    private static final Map<String, NodeDef> REGISTRY = buildRegistry();
    private static final List<NodeDef> ALL = List.copyOf(REGISTRY.values());
    private static final Map<Tree, List<NodeDef>> BY_TREE = indexByTree();

    private NodeDefs() {
    }

    public static NodeDef get(String id) {
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

    /** Total skill-point cost of fully purchasing every node in the given tree. */
    public static int totalTreeCost(Tree tree) {
        int total = 0;
        for (NodeDef def : getByTree(tree)) {
            for (int cost : def.costs()) {
                total += cost;
            }
        }
        return total;
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

    private static Map<String, NodeDef> buildRegistry() {
        Map<String, NodeDef> map = new LinkedHashMap<>();

        // ----- Resonance tree — Core -----
        node(map, "disturbed_zone_unlock", "Disturbed Zone Unlock", Tree.RESONANCE, "Core",
                DISTURBED_ZONE_UNLOCK_COSTS, DISTURBED_ZONE_UNLOCK_LEVEL_REQS, List.of(),
                false, null, false);

        // ----- Resonance tree — Vein -----
        node(map, "vein_expansion", "Vein Expansion", Tree.RESONANCE, "Vein",
                VEIN_EXPANSION_COSTS, VEIN_EXPANSION_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "vein_proliferation", "Vein Proliferation", Tree.RESONANCE, "Vein",
                VEIN_PROLIFERATION_COSTS, VEIN_PROLIFERATION_LEVEL_REQS, List.of(pre("vein_expansion", 1)),
                false, null, false);
        node(map, "deep_veins", "Deep Veins", Tree.RESONANCE, "Vein",
                DEEP_VEINS_COSTS, DEEP_VEINS_LEVEL_REQS, List.of(pre("vein_proliferation", 2)),
                false, null, false);
        node(map, "twin_veins", "Twin Veins", Tree.RESONANCE, "Vein",
                TWIN_VEINS_COSTS, TWIN_VEINS_LEVEL_REQS, List.of(pre("vein_expansion", 3)),
                false, null, false);
        node(map, "vault_echo", "Vault Echo", Tree.RESONANCE, "Vein",
                VAULT_ECHO_COSTS, VAULT_ECHO_LEVEL_REQS, List.of(pre("vein_expansion", 2)),
                false, null, false);

        // ----- Resonance tree — Ore Quality -----
        node(map, "common_ore_boost", "Common Ore Boost", Tree.RESONANCE, "Ore Quality",
                COMMON_ORE_BOOST_COSTS, COMMON_ORE_BOOST_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "uncommon_ore_boost", "Uncommon Ore Boost", Tree.RESONANCE, "Ore Quality",
                UNCOMMON_ORE_BOOST_COSTS, UNCOMMON_ORE_BOOST_LEVEL_REQS, List.of(pre("common_ore_boost", 1)),
                false, null, false);
        node(map, "rare_ore_boost", "Rare Ore Boost", Tree.RESONANCE, "Ore Quality",
                RARE_ORE_BOOST_COSTS, RARE_ORE_BOOST_LEVEL_REQS, List.of(pre("uncommon_ore_boost", 1)),
                false, null, false);
        node(map, "ancient_traces", "Ancient Traces", Tree.RESONANCE, "Ore Quality",
                ANCIENT_TRACES_COSTS, ANCIENT_TRACES_LEVEL_REQS, List.of(pre("rare_ore_boost", 2)),
                false, null, false);
        node(map, "gravel_purge", "Gravel Purge", Tree.RESONANCE, "Ore Quality",
                GRAVEL_PURGE_COSTS, GRAVEL_PURGE_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "stone_reduction", "Stone Reduction", Tree.RESONANCE, "Ore Quality",
                STONE_REDUCTION_COSTS, STONE_REDUCTION_LEVEL_REQS, List.of(pre("gravel_purge", 1)),
                false, null, false);
        node(map, "geode_clusters", "Geode Clusters", Tree.RESONANCE, "Ore Quality",
                GEODE_CLUSTERS_COSTS, GEODE_CLUSTERS_LEVEL_REQS, List.of(pre("stone_reduction", 1)),
                false, null, false);

        // ----- Resonance tree — Fortune -----
        node(map, "ore_sense", "Ore Sense", Tree.RESONANCE, "Fortune",
                ORE_SENSE_COSTS, ORE_SENSE_LEVEL_REQS, List.of(pre("vein_proliferation", 1)),
                false, null, false);
        node(map, "ore_doubling", "Ore Doubling", Tree.RESONANCE, "Fortune",
                ORE_DOUBLING_COSTS, ORE_DOUBLING_LEVEL_REQS, List.of(pre("ore_sense", 1)),
                false, null, false);
        node(map, "runic_attunement", "Runic Attunement", Tree.RESONANCE, "Fortune",
                RUNIC_ATTUNEMENT_COSTS, RUNIC_ATTUNEMENT_LEVEL_REQS, List.of(pre("ore_doubling", 1)),
                false, null, false);
        node(map, "smelters_intuition", "Smelter's Intuition", Tree.RESONANCE, "Fortune",
                SMELTERS_INTUITION_COSTS, SMELTERS_INTUITION_LEVEL_REQS, List.of(pre("ore_doubling", 1)),
                false, null, false);

        // ----- Resonance tree — XP and Stone -----
        node(map, "stone_memory", "Stone Memory", Tree.RESONANCE, "XP and Stone",
                STONE_MEMORY_COSTS, STONE_MEMORY_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "ancient_knowledge", "Ancient Knowledge", Tree.RESONANCE, "XP and Stone",
                ANCIENT_KNOWLEDGE_COSTS, ANCIENT_KNOWLEDGE_LEVEL_REQS, List.of(pre("stone_memory", 1)),
                false, null, false);

        // ----- Resonance tree — Hunger -----
        node(map, "efficient_miner", "Efficient Miner", Tree.RESONANCE, "Hunger",
                EFFICIENT_MINER_COSTS, EFFICIENT_MINER_LEVEL_REQS, List.of(),
                false, null, false);

        // ----- Resonance tree — Utility -----
        node(map, "resonance_magnetism", "Resonance Magnetism", Tree.RESONANCE, "Utility",
                RESONANCE_MAGNETISM_COSTS, RESONANCE_MAGNETISM_LEVEL_REQS, List.of(),
                false, "hoarders_instinct", false);
        node(map, "hoarders_instinct", "Hoarder's Instinct", Tree.RESONANCE, "Utility",
                HOARDERS_INSTINCT_COSTS, HOARDERS_INSTINCT_LEVEL_REQS, List.of(),
                false, "resonance_magnetism", false);
        node(map, "automated_extraction", "Automated Extraction", Tree.RESONANCE, "Utility",
                AUTOMATED_EXTRACTION_COSTS, AUTOMATED_EXTRACTION_LEVEL_REQS, List.of(pre("vault_presence", 1)),
                false, null, false);
        node(map, "vault_presence", "Vault Presence", Tree.RESONANCE, "Utility",
                VAULT_PRESENCE_COSTS, VAULT_PRESENCE_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "vault_expansion", "Vault Expansion", Tree.RESONANCE, "Utility",
                VAULT_EXPANSION_COSTS, VAULT_EXPANSION_LEVEL_REQS,
                List.of(pre("rare_ore_boost", 3), pre("vein_expansion", 5), pre("efficient_miner", 4)),
                false, null, false);

        // ----- Resonance tree — FTB Ultimine -----
        node(map, "ultimine_expansion", "Ultimine Expansion", Tree.RESONANCE, "Ultimine",
                ULTIMINE_EXPANSION_COSTS, ULTIMINE_EXPANSION_LEVEL_REQS, List.of(),
                false, null, true);
        node(map, "ultimine_safety", "Ultimine Safety", Tree.RESONANCE, "Ultimine",
                ULTIMINE_SAFETY_COSTS, ULTIMINE_SAFETY_LEVEL_REQS, List.of(pre("ultimine_expansion", 1)),
                false, null, true);

        // ----- Resonance tree — Tradeoffs -----
        node(map, "volatile_veins", "Volatile Veins", Tree.RESONANCE, "Tradeoff",
                VOLATILE_VEINS_COSTS, VOLATILE_VEINS_LEVEL_REQS, List.of(pre("vein_expansion", 2)),
                true, null, false);
        node(map, "ultimine_gambit", "Ultimine Gambit", Tree.RESONANCE, "Tradeoff",
                ULTIMINE_GAMBIT_COSTS, ULTIMINE_GAMBIT_LEVEL_REQS,
                List.of(pre("volatile_veins", 1), pre("ultimine_expansion", 1)),
                true, null, true);
        node(map, "greedy_seams", "Greedy Seams", Tree.RESONANCE, "Tradeoff",
                GREEDY_SEAMS_COSTS, GREEDY_SEAMS_LEVEL_REQS, List.of(pre("vein_expansion", 1)),
                true, null, false);
        node(map, "stone_curse", "Stone Curse", Tree.RESONANCE, "Tradeoff",
                STONE_CURSE_COSTS, STONE_CURSE_LEVEL_REQS, List.of(pre("stone_memory", 2)),
                true, null, false);
        node(map, "vault_fever", "Vault Fever", Tree.RESONANCE, "Tradeoff",
                VAULT_FEVER_COSTS, VAULT_FEVER_LEVEL_REQS, List.of(pre("efficient_miner", 2)),
                true, null, false);
        node(map, "tithe", "Tithe", Tree.RESONANCE, "Tradeoff",
                TITHE_COSTS, TITHE_LEVEL_REQS, List.of(),
                true, null, false);

        // ----- Resonance tree — Exclusive pairs -----
        node(map, "abundance", "Abundance", Tree.RESONANCE, "Exclusive",
                ABUNDANCE_COSTS, ABUNDANCE_LEVEL_REQS, List.of(pre("vein_proliferation", 2)),
                false, "motherlode", false);
        node(map, "motherlode", "Motherlode", Tree.RESONANCE, "Exclusive",
                MOTHERLODE_COSTS, MOTHERLODE_LEVEL_REQS, List.of(pre("vein_proliferation", 2)),
                false, "abundance", false);
        node(map, "vaults_blessing", "Vault's Blessing", Tree.RESONANCE, "Exclusive",
                VAULTS_BLESSING_COSTS, VAULTS_BLESSING_LEVEL_REQS, List.of(),
                false, "vaults_purity", false);
        node(map, "vaults_purity", "Vault's Purity", Tree.RESONANCE, "Exclusive",
                VAULTS_PURITY_COSTS, VAULTS_PURITY_LEVEL_REQS, List.of(),
                false, "vaults_blessing", false);

        // ----- Animus tree — Zone Enhancement -----
        node(map, "zone_frequency", "Zone Frequency", Tree.ANIMUS, "Zone Enhancement",
                ZONE_FREQUENCY_COSTS, ZONE_FREQUENCY_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "zone_pack_size", "Zone Pack Size", Tree.ANIMUS, "Zone Enhancement",
                ZONE_PACK_SIZE_COSTS, ZONE_PACK_SIZE_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "zone_radius", "Zone Radius", Tree.ANIMUS, "Zone Enhancement",
                ZONE_RADIUS_COSTS, ZONE_RADIUS_LEVEL_REQS, List.of(pre("zone_frequency", 1)),
                false, null, false);
        node(map, "mob_diversity", "Mob Diversity", Tree.ANIMUS, "Zone Enhancement",
                MOB_DIVERSITY_COSTS, MOB_DIVERSITY_LEVEL_REQS, List.of(),
                false, null, false);

        // ----- Animus tree — Mob Rewards -----
        node(map, "reapers_claim", "Reaper's Claim", Tree.ANIMUS, "Mob Rewards",
                REAPERS_CLAIM_COSTS, REAPERS_CLAIM_LEVEL_REQS, List.of(pre("zone_frequency", 1)),
                false, null, false);
        node(map, "corrupted_veins", "Corrupted Veins", Tree.ANIMUS, "Mob Rewards",
                CORRUPTED_VEINS_COSTS, CORRUPTED_VEINS_LEVEL_REQS, List.of(pre("zone_frequency", 2)),
                false, null, false);
        node(map, "plunderers_share", "Plunderer's Share", Tree.ANIMUS, "Mob Rewards",
                PLUNDERERS_SHARE_COSTS, PLUNDERERS_SHARE_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "animus_amplifier", "Animus Amplifier", Tree.ANIMUS, "Mob Rewards",
                ANIMUS_AMPLIFIER_COSTS, ANIMUS_AMPLIFIER_LEVEL_REQS, List.of(),
                false, null, false);
        node(map, "soul_harvest", "Soul Harvest", Tree.ANIMUS, "Mob Rewards",
                SOUL_HARVEST_COSTS, SOUL_HARVEST_LEVEL_REQS,
                List.of(pre("reapers_claim", 3), pre("corrupted_veins", 2), pre("plunderers_share", 2)),
                false, null, false);

        return map;
    }

    private static Prereq pre(String nodeId, int minTier) {
        return new Prereq(nodeId, minTier);
    }

    private static void node(Map<String, NodeDef> map, String id, String name, Tree tree, String branch,
                             int[] costs, int[] levelReqs, List<Prereq> prereqs,
                             boolean tradeoff, String exclusiveWith, boolean ultimineOnly) {
        NodeDef def = new NodeDef(id, name, tree, branch, costs, levelReqs, prereqs, tradeoff, exclusiveWith, ultimineOnly);
        if (map.put(id, def) != null) {
            throw new IllegalStateException("Duplicate node id: " + id);
        }
    }
}
