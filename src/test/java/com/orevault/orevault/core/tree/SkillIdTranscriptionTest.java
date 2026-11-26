package com.orevault.orevault.core.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The skill ids typed from §6.1's headings, one list per section, in §6.1's order. Each id is the heading lower-cased,
 * apostrophes dropped and every other run of characters outside {@code [a-z0-9]} one underscore, with no exceptions
 * (Manager ruling, #208). Fork options are petals, not skills, and are in {@code ForkOptionTest}. Writer signs the names,
 * Balancer the twelve travel stats.
 */
class SkillIdTranscriptionTest {
    static final List<String> SKILLS = List.of(
            "vein_expansion", "stone_memory", "gravel_purge", "miners_constitution", "sure_footing", "deep_breath",
            "novices_luck", "apprentices_ledger", "shallow_grace", "guide_vein", "salvagers_eye", "vein_proliferation",
            "deep_veins", "stone_reduction", "vein_shaping", "deep_habit", "long_delve", "volatile_veins", "molten_seam",
            "greedy_seams", "resonant_overload", "ore_attunement", "geode_clusters", "ancient_traces",
            "ancient_knowledge", "stonecutters_patience", "calloused_hands", "vein_sight", "stonecaller",
            "prospectors_eye", "stone_curse", "vein_fortune", "ore_working", "runic_attunement", "kindred_rock",
            "highwater_mark", "brittle_stone", "dense_stone", "vault_fever", "vault_presence", "automated_extraction",
            "far_reach", "seismic_sense", "wanderers_cache", "frontier_bonus", "pathfinders_claim", "homeward_seam",
            "the_vault_keeps_it", "second_wind", "second_wind_homeward", "vaults_blessing", "vaults_purity", "tithe",
            "vault_echo", "echo_chamber", "twin_veins", "last_ore", "clean_cut", "vein_discipline", "chapters_end",
            "deep_harvest", "resonant_draw", "ultimine_expansion", "ultimine_safety",
            "volatile_veins_ultimine_gambit");

    static final List<String> TRAVEL_STATS = List.of(
            "mining_speed", "resonance_gain", "vanilla_xp_from_ore", "move_speed", "block_reach", "max_health",
            "tool_care", "tool_charge", "safe_fall", "armour", "masons_reach", "kit_care");

    static final List<String> FLAVOUR_NOTABLES = List.of(
            "spelunkers_stride", "landscapers_swing", "seam_courier", "kiln_touch", "wayfarers_lungs", "vault_forged",
            "resonant_dynamo", "hammer_and_paxel_affinity", "harvesters_lot", "tempered", "bookseam", "steady_chain",
            "delvers_lungs", "vault_smith", "ore_imprint", "gem_seam", "unyielding_edge", "resonant_trait",
            "blast_wise", "budding_seam", "ascending_edge");

    static final List<String> KEYSTONES = List.of(
            "vault_expansion", "full_spectrum", "silent_stone", "resonant_symbiosis", "bedrock_communion", "long_arm",
            "glass_edge", "galvanic_seam", "echoing_edge");

    @Test
    void sectionSizes() {
        assertEquals(65, SKILLS.size());
        assertEquals(12, TRAVEL_STATS.size(), "§6.1 Travel stats: twelve");
        assertEquals(21, FLAVOUR_NOTABLES.size());
        assertEquals(9, KEYSTONES.size());
    }

    @Test
    void thereAre107Constants() {
        assertEquals(107, SkillId.values().length);
    }

    @Test
    void theIdsAreExactlyTheTranscribedListsInOrder() {
        List<String> expected = new ArrayList<>();
        expected.addAll(SKILLS);
        expected.addAll(TRAVEL_STATS);
        expected.addAll(FLAVOUR_NOTABLES);
        expected.addAll(KEYSTONES);
        assertEquals(expected, Arrays.stream(SkillId.values()).map(SkillId::id).toList());
    }
}
