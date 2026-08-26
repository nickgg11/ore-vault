package com.orevault.orevault.skill;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of every skill node defined in the design spec (section 6). All numbers come
 * from {@link NodeCosts}. Prerequisite entries are "nodeId:minTier" strings.
 */
public final class NodeDefs {
    public static final String RESONANCE = "resonance";
    public static final String ANIMUS = "animus";

    private static final Map<String, NodeDef> NODES = new LinkedHashMap<>();

    private NodeDefs() {
    }

    private static void node(String id, String tree, String branch, int maxTier, int[] costs,
                             int[] levelReqs, String[][] prereqs, boolean tradeoff, String[] exclusives,
                             boolean ultimineOnly, boolean mekanismTiers, boolean keystone) {
        NODES.put(id, new NodeDef(id, tree, branch,
                "orevault.node." + id + ".name", "orevault.node." + id + ".desc",
                maxTier, costs, levelReqs, prereqs, tradeoff, exclusives, ultimineOnly, mekanismTiers, keystone));
    }

    static {
        // === Resonance tree ===
        node("disturbed_zone_unlock", RESONANCE, "core", 1,
                new int[]{NodeCosts.DISTURBED_ZONE_UNLOCK_COST}, new int[]{NodeCosts.DISTURBED_ZONE_UNLOCK_LEVEL_REQ},
                new String[][]{{}}, false, null, false, false, false);

        node("vein_expansion", RESONANCE, "vein", 5,
                NodeCosts.VEIN_EXPANSION_COSTS, NodeCosts.VEIN_EXPANSION_LEVEL_REQS,
                new String[][]{{}, {"vein_expansion:1"}, {"vein_expansion:2"}, {"vein_expansion:3"}, {"vein_expansion:4"}},
                false, null, false, false, false);
        node("vein_proliferation", RESONANCE, "vein", 5,
                NodeCosts.VEIN_PROLIFERATION_COSTS, NodeCosts.VEIN_PROLIFERATION_LEVEL_REQS,
                new String[][]{{"vein_expansion:1"}, {"vein_proliferation:1"}, {"vein_proliferation:2"}, {"vein_proliferation:3"}, {"vein_proliferation:4"}},
                false, null, false, false, false);
        node("deep_veins", RESONANCE, "vein", 2,
                NodeCosts.DEEP_VEINS_COSTS, NodeCosts.DEEP_VEINS_LEVEL_REQS,
                new String[][]{{"vein_proliferation:2"}, {"deep_veins:1"}},
                false, null, false, false, false);
        node("twin_veins", RESONANCE, "vein", 3,
                NodeCosts.TWIN_VEINS_COSTS, NodeCosts.TWIN_VEINS_LEVEL_REQS,
                new String[][]{{"vein_expansion:3"}, {"twin_veins:1"}, {"twin_veins:2"}},
                false, null, false, false, false);
        node("vault_echo", RESONANCE, "vein", 3,
                NodeCosts.VAULT_ECHO_COSTS, NodeCosts.VAULT_ECHO_LEVEL_REQS,
                new String[][]{{"vein_expansion:2"}, {"vault_echo:1"}, {"vault_echo:2"}},
                false, null, false, false, false);

        node("common_ore_boost", RESONANCE, "ore_quality", 3,
                NodeCosts.COMMON_ORE_BOOST_COSTS, NodeCosts.COMMON_ORE_BOOST_LEVEL_REQS,
                new String[][]{{}, {"common_ore_boost:1"}, {"common_ore_boost:2"}},
                false, null, false, false, false);
        node("uncommon_ore_boost", RESONANCE, "ore_quality", 3,
                NodeCosts.UNCOMMON_ORE_BOOST_COSTS, NodeCosts.UNCOMMON_ORE_BOOST_LEVEL_REQS,
                new String[][]{{"common_ore_boost:1"}, {"uncommon_ore_boost:1"}, {"uncommon_ore_boost:2"}},
                false, null, false, false, false);
        node("rare_ore_boost", RESONANCE, "ore_quality", 3,
                NodeCosts.RARE_ORE_BOOST_COSTS, NodeCosts.RARE_ORE_BOOST_LEVEL_REQS,
                new String[][]{{"uncommon_ore_boost:1"}, {"rare_ore_boost:1"}, {"rare_ore_boost:2"}},
                false, null, false, false, false);
        node("ancient_traces", RESONANCE, "ore_quality", 2,
                NodeCosts.ANCIENT_TRACES_COSTS, NodeCosts.ANCIENT_TRACES_LEVEL_REQS,
                new String[][]{{"rare_ore_boost:2"}, {"ancient_traces:1"}},
                false, null, false, false, false);
        node("gravel_purge", RESONANCE, "ore_quality", 1,
                new int[]{NodeCosts.GRAVEL_PURGE_COST}, new int[]{NodeCosts.GRAVEL_PURGE_LEVEL_REQ},
                new String[][]{{}}, false, null, false, false, false);
        node("stone_reduction", RESONANCE, "ore_quality", 2,
                NodeCosts.STONE_REDUCTION_COSTS, NodeCosts.STONE_REDUCTION_LEVEL_REQS,
                new String[][]{{"gravel_purge:1"}, {"stone_reduction:1"}},
                false, null, false, false, false);
        node("geode_clusters", RESONANCE, "ore_quality", 2,
                NodeCosts.GEODE_CLUSTERS_COSTS, NodeCosts.GEODE_CLUSTERS_LEVEL_REQS,
                new String[][]{{"stone_reduction:1"}, {"geode_clusters:1"}},
                false, null, false, false, false);

        node("ore_sense", RESONANCE, "fortune", 3,
                NodeCosts.ORE_SENSE_COSTS, NodeCosts.ORE_SENSE_LEVEL_REQS,
                new String[][]{{"vein_proliferation:1"}, {"ore_sense:1"}, {"ore_sense:2"}},
                false, null, false, false, false);
        node("ore_doubling", RESONANCE, "fortune", 6,
                NodeCosts.ORE_DOUBLING_COSTS, NodeCosts.ORE_DOUBLING_LEVEL_REQS,
                new String[][]{{"ore_sense:1"}, {"ore_doubling:1"}, {"ore_doubling:2"}, {"ore_doubling:3"}, {"ore_doubling:4"}, {"ore_doubling:5"}},
                false, null, false, true, false);
        node("runic_attunement", RESONANCE, "fortune", 3,
                NodeCosts.RUNIC_ATTUNEMENT_COSTS, NodeCosts.RUNIC_ATTUNEMENT_LEVEL_REQS,
                new String[][]{{"ore_doubling:1"}, {"runic_attunement:1"}, {"runic_attunement:2"}},
                false, null, false, false, false);
        node("smelters_intuition", RESONANCE, "fortune", 3,
                NodeCosts.SMELTERS_INTUITION_COSTS, NodeCosts.SMELTERS_INTUITION_LEVEL_REQS,
                new String[][]{{"ore_doubling:1"}, {"smelters_intuition:1"}, {"smelters_intuition:2"}},
                false, null, false, false, false);

        node("stone_memory", RESONANCE, "xp_stone", 5,
                NodeCosts.STONE_MEMORY_COSTS, NodeCosts.STONE_MEMORY_LEVEL_REQS,
                new String[][]{{}, {"stone_memory:1"}, {"stone_memory:2"}, {"stone_memory:3"}, {"stone_memory:4"}},
                false, null, false, false, false);
        node("ancient_knowledge", RESONANCE, "xp_stone", 3,
                NodeCosts.ANCIENT_KNOWLEDGE_COSTS, NodeCosts.ANCIENT_KNOWLEDGE_LEVEL_REQS,
                new String[][]{{"stone_memory:1"}, {"ancient_knowledge:1"}, {"ancient_knowledge:2"}},
                false, null, false, false, false);

        node("efficient_miner", RESONANCE, "hunger", 5,
                NodeCosts.EFFICIENT_MINER_COSTS, NodeCosts.EFFICIENT_MINER_LEVEL_REQS,
                new String[][]{{}, {"efficient_miner:1"}, {"efficient_miner:2"}, {"efficient_miner:3"}, {"efficient_miner:4"}},
                false, null, false, false, false);

        node("resonance_magnetism", RESONANCE, "utility", 3,
                NodeCosts.RESONANCE_MAGNETISM_COSTS, NodeCosts.RESONANCE_MAGNETISM_LEVEL_REQS,
                new String[][]{{}, {"resonance_magnetism:1"}, {"resonance_magnetism:2"}},
                false, new String[]{"hoarders_instinct"}, false, false, false);
        node("hoarders_instinct", RESONANCE, "utility", 1,
                new int[]{NodeCosts.HOARDERS_INSTINCT_COST}, new int[]{NodeCosts.HOARDERS_INSTINCT_LEVEL_REQ},
                new String[][]{{}},
                false, new String[]{"resonance_magnetism"}, false, false, false);
        node("automated_extraction", RESONANCE, "utility", 2,
                NodeCosts.AUTOMATED_EXTRACTION_COSTS, NodeCosts.AUTOMATED_EXTRACTION_LEVEL_REQS,
                new String[][]{{"vault_presence:1"}, {"automated_extraction:1"}},
                false, null, false, false, false);
        node("vault_presence", RESONANCE, "utility", 3,
                NodeCosts.VAULT_PRESENCE_COSTS, NodeCosts.VAULT_PRESENCE_LEVEL_REQS,
                new String[][]{{}, {"vault_presence:1"}, {"vault_presence:2"}},
                false, null, false, false, false);
        node("vault_expansion", RESONANCE, "utility", 1,
                new int[]{NodeCosts.VAULT_EXPANSION_COST}, new int[]{NodeCosts.VAULT_EXPANSION_LEVEL_REQ},
                new String[][]{{"rare_ore_boost:3", "vein_expansion:5", "efficient_miner:4"}},
                false, null, false, false, true);

        node("ultimine_expansion", RESONANCE, "ultimine", 3,
                NodeCosts.ULTIMINE_EXPANSION_COSTS, NodeCosts.ULTIMINE_EXPANSION_LEVEL_REQS,
                new String[][]{{}, {"ultimine_expansion:1"}, {"ultimine_expansion:2"}},
                false, null, true, false, false);
        node("ultimine_safety", RESONANCE, "ultimine", 2,
                NodeCosts.ULTIMINE_SAFETY_COSTS, NodeCosts.ULTIMINE_SAFETY_LEVEL_REQS,
                new String[][]{{"ultimine_expansion:1"}, {"ultimine_safety:1"}},
                false, null, true, false, false);

        node("volatile_veins", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.VOLATILE_VEINS_COST}, new int[]{NodeCosts.VOLATILE_VEINS_LEVEL_REQ},
                new String[][]{{"vein_expansion:2"}},
                true, null, false, false, false);
        node("ultimine_gambit", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.ULTIMINE_GAMBIT_COST}, new int[]{NodeCosts.ULTIMINE_GAMBIT_LEVEL_REQ},
                new String[][]{{"volatile_veins:1", "ultimine_expansion:1"}},
                true, null, true, false, false);
        node("greedy_seams", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.GREEDY_SEAMS_COST}, new int[]{NodeCosts.GREEDY_SEAMS_LEVEL_REQ},
                new String[][]{{"vein_expansion:1"}},
                true, null, false, false, false);
        node("stone_curse", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.STONE_CURSE_COST}, new int[]{NodeCosts.STONE_CURSE_LEVEL_REQ},
                new String[][]{{"stone_memory:2"}},
                true, null, false, false, false);
        node("vault_fever", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.VAULT_FEVER_COST}, new int[]{NodeCosts.VAULT_FEVER_LEVEL_REQ},
                new String[][]{{"efficient_miner:2"}},
                true, null, false, false, false);
        node("tithe", RESONANCE, "tradeoff", 1,
                new int[]{NodeCosts.TITHE_COST}, new int[]{NodeCosts.TITHE_LEVEL_REQ},
                new String[][]{{}},
                true, null, false, false, false);

        node("abundance", RESONANCE, "exclusive", 2,
                NodeCosts.ABUNDANCE_COSTS, NodeCosts.ABUNDANCE_LEVEL_REQS,
                new String[][]{{"vein_proliferation:2"}, {"abundance:1"}},
                false, new String[]{"motherlode"}, false, false, false);
        node("motherlode", RESONANCE, "exclusive", 2,
                NodeCosts.MOTHERLODE_COSTS, NodeCosts.MOTHERLODE_LEVEL_REQS,
                new String[][]{{"vein_proliferation:2"}, {"motherlode:1"}},
                false, new String[]{"abundance"}, false, false, false);
        node("vaults_blessing", RESONANCE, "exclusive", 1,
                new int[]{NodeCosts.VAULTS_BLESSING_COST}, new int[]{NodeCosts.VAULTS_BLESSING_LEVEL_REQ},
                new String[][]{{}},
                false, new String[]{"vaults_purity"}, false, false, false);
        node("vaults_purity", RESONANCE, "exclusive", 1,
                new int[]{NodeCosts.VAULTS_PURITY_COST}, new int[]{NodeCosts.VAULTS_PURITY_LEVEL_REQ},
                new String[][]{{}},
                false, new String[]{"vaults_blessing"}, false, false, false);

        // === Animus (mob) tree ===
        node("zone_frequency", ANIMUS, "zone", 4,
                NodeCosts.ZONE_FREQUENCY_COSTS, NodeCosts.ZONE_FREQUENCY_LEVEL_REQS,
                new String[][]{{}, {"zone_frequency:1"}, {"zone_frequency:2"}, {"zone_frequency:3"}},
                false, null, false, false, false);
        node("zone_pack_size", ANIMUS, "zone", 3,
                NodeCosts.ZONE_PACK_SIZE_COSTS, NodeCosts.ZONE_PACK_SIZE_LEVEL_REQS,
                new String[][]{{}, {"zone_pack_size:1"}, {"zone_pack_size:2"}},
                false, null, false, false, false);
        node("zone_radius", ANIMUS, "zone", 3,
                NodeCosts.ZONE_RADIUS_COSTS, NodeCosts.ZONE_RADIUS_LEVEL_REQS,
                new String[][]{{"zone_frequency:1"}, {"zone_radius:1"}, {"zone_radius:2"}},
                false, null, false, false, false);
        node("mob_diversity", ANIMUS, "zone", 4,
                NodeCosts.MOB_DIVERSITY_COSTS, NodeCosts.MOB_DIVERSITY_LEVEL_REQS,
                new String[][]{{}, {"mob_diversity:1"}, {"mob_diversity:2"}, {"mob_diversity:3"}},
                false, null, false, false, false);

        node("reapers_claim", ANIMUS, "rewards", 3,
                NodeCosts.REAPERS_CLAIM_COSTS, NodeCosts.REAPERS_CLAIM_LEVEL_REQS,
                new String[][]{{"zone_frequency:1"}, {"reapers_claim:1"}, {"reapers_claim:2"}},
                false, null, false, false, false);
        node("corrupted_veins", ANIMUS, "rewards", 3,
                NodeCosts.CORRUPTED_VEINS_COSTS, NodeCosts.CORRUPTED_VEINS_LEVEL_REQS,
                new String[][]{{"zone_frequency:2"}, {"corrupted_veins:1"}, {"corrupted_veins:2"}},
                false, null, false, false, false);
        node("plunderers_share", ANIMUS, "rewards", 3,
                NodeCosts.PLUNDERERS_SHARE_COSTS, NodeCosts.PLUNDERERS_SHARE_LEVEL_REQS,
                new String[][]{{}, {"plunderers_share:1"}, {"plunderers_share:2"}},
                false, null, false, false, false);
        node("animus_amplifier", ANIMUS, "rewards", 3,
                NodeCosts.ANIMUS_AMPLIFIER_COSTS, NodeCosts.ANIMUS_AMPLIFIER_LEVEL_REQS,
                new String[][]{{}, {"animus_amplifier:1"}, {"animus_amplifier:2"}},
                false, null, false, false, false);
        node("soul_harvest", ANIMUS, "rewards", 1,
                new int[]{NodeCosts.SOUL_HARVEST_COST}, new int[]{NodeCosts.SOUL_HARVEST_LEVEL_REQ},
                new String[][]{{"reapers_claim:3", "corrupted_veins:2", "plunderers_share:2"}},
                false, null, false, false, true);
    }

    public static Optional<NodeDef> get(String id) {
        return Optional.ofNullable(NODES.get(id));
    }

    public static java.util.Collection<NodeDef> all() {
        return NODES.values();
    }

    public static java.util.List<NodeDef> forTree(String treeId) {
        return NODES.values().stream().filter(n -> n.treeId().equals(treeId)).toList();
    }

    /** Total skill point cost of an entire tree (sum of every tier's cost). */
    public static int totalTreeCost(String treeId) {
        int sum = 0;
        for (NodeDef def : NODES.values()) {
            if (def.treeId().equals(treeId)) {
                for (int cost : def.costs()) {
                    sum += cost;
                }
            }
        }
        return sum;
    }
}
