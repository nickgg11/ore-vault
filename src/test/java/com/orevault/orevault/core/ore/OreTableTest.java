package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ADR 0009's pure golden fixture: vanilla 26.1.2's features from research section 2 as plain numbers, through steps 3, 4,
 * 8 and 9 and the overrides. The gametest runs the same checks on the real classifier; this one pins the arithmetic.
 */
class OreTableTest {
    private static OreTable vanilla() {
        return vanilla(Overrides.NONE);
    }

    private static OreTable vanilla(Overrides overrides) {
        return OreTable.build(VanillaOres.samples(), VanillaOres.ORE_TAG, overrides, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
    }

    private static OreTable.Entry entry(OreTable table, String block) {
        OreTable.Entry entry = table.lookup(block);
        assertNotNull(entry, block + " has no entry");
        return entry;
    }

    @Test
    void theEightVanillaOresGetTheirClasses() {
        OreTable table = vanilla();
        assertEquals(8, table.entries().size(), table.entries().toString());
        VanillaOres.RARITY.forEach((ore, rarity) -> assertEquals(rarity, entry(table, ore).rarity(), ore));
    }

    @Test
    void expectedBlocksAreWithinTenPercentOfTheResearch() {
        OreTable table = vanilla();
        VanillaOres.EXPECTED_BLOCKS.forEach((ore, expected) -> {
            double actual = entry(table, ore).expectedBlocks();
            assertTrue(Math.abs(actual - expected) <= 0.10 * expected, ore + ": " + actual + " against " + expected);
        });
    }

    /** ADR 0009 Tests: lapis has the thinnest margin, 4.97 blocks a vein against 4.5, and is asserted on its own. */
    @Test
    void lapisClearsTheRareLineByItsOwnMargin() {
        OreTable.Entry lapis = entry(vanilla(), "minecraft:lapis_ore");
        assertEquals(4.97, lapis.meanBlocksPerVein(), 1e-9);
        assertTrue(lapis.meanBlocksPerVein() >= RarityRule.RARE_BLOCKS_PER_VEIN);
        assertTrue(lapis.expectedBlocks() < RarityRule.COMMON_BLOCKS_PER_CHUNK);
        assertEquals(Rarity.UNCOMMON, lapis.rarity());
    }

    /** Step 3: copper's dripstone-only large feature and badlands' extra gold are left out, emerald keeps its mountain one. */
    @Test
    void biomeOnlyFeaturesCountOnlyForAnOreWithNoAllBiomeOne() {
        OreTable table = vanilla();
        assertEquals(List.of("minecraft:ore_copper"), entry(table, "minecraft:copper_ore").features());
        assertEquals(List.of("minecraft:ore_gold", "minecraft:ore_gold_lower"), entry(table, "minecraft:gold_ore").features());
        assertEquals(List.of("minecraft:ore_emerald"), entry(table, "minecraft:emerald_ore").features());
        assertEquals(List.of("minecraft:ore_coal_lower", "minecraft:ore_coal_upper"), entry(table, "minecraft:coal_ore").features());
    }

    /** ADR 0009: all-biome features weigh 1.0, so the common weights are §6.1's 1,108, 646 and 134; emerald's carries 0.18. */
    @Test
    void weightsCarryTheBiomeShareOfBiomeOnlyFeaturesOnly() {
        OreTable table = vanilla();
        assertEquals(1108, entry(table, "minecraft:coal_ore").weight(), 0.1 * 1108);
        assertEquals(646, entry(table, "minecraft:iron_ore").weight(), 0.1 * 646);
        assertEquals(134, entry(table, "minecraft:copper_ore").weight(), 0.1 * 134);
        assertEquals(134.3, entry(table, "minecraft:copper_ore").weight(), 1e-9, "53/54 is all-biome and weighs 1.0");
        OreTable.Entry emerald = entry(table, "minecraft:emerald_ore");
        assertEquals(0.18, emerald.weight() / emerald.expectedBlocks(), 1e-9);
    }

    /** The classifier's table gives the picker the fixture it was tested on in E4-S1b. */
    @Test
    void theCandidatesAreThePickersFixture() {
        List<OrePicker.Candidate> fromTable = vanilla().candidates();
        List<OrePicker.Candidate> fixture = new ArrayList<>(VanillaOres.candidates());
        fixture.sort((a, b) -> a.groupId().compareTo(b.groupId()));
        assertEquals(fixture.size(), fromTable.size());
        for (int i = 0; i < fixture.size(); i++) {
            OrePicker.Candidate expected = fixture.get(i);
            OrePicker.Candidate actual = fromTable.get(i);
            assertEquals(expected.groupId(), actual.groupId());
            assertEquals(expected.rarity(), actual.rarity());
            assertEquals(expected.weight(), actual.weight(), 1e-9, expected.groupId());
            assertArrayEquals(expected.profile().masses(), actual.profile().masses(), 1e-12, expected.groupId());
        }
    }

    /** Step 4: both variants are one ore, under the stone-band block's id, and each band places its own variant. */
    @Test
    void variantsAreOneOreUnderTheStoneBlocksId() {
        OreTable table = vanilla();
        OreTable.Entry iron = entry(table, "minecraft:deepslate_iron_ore");
        assertEquals("minecraft:iron_ore", iron.groupId());
        assertEquals("minecraft:iron_ore", iron.stoneBlock());
        assertEquals("minecraft:deepslate_iron_ore", iron.deepslateBlock());
        assertEquals(List.of("minecraft:deepslate_iron_ore", "minecraft:iron_ore"), iron.blocks());
        assertTrue(iron == table.lookup("minecraft:iron_ore"));
    }

    /** Old PR #117 made the shape of the API: no default rarity, so these pay nothing (QA-E4-6). */
    @Test
    void aBlockThatIsNotAClassifiedOreHasNoEntry() {
        OreTable table = vanilla();
        for (String block : List.of("minecraft:stone", "minecraft:deepslate", "minecraft:gravel", "minecraft:clay",
                "minecraft:ancient_debris", "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore")) {
            assertNull(table.lookup(block), block);
        }
    }

    /** Step 9: a tag member no overworld feature places is unclassified; ancient debris is the vanilla case. */
    @Test
    void tagMembersWithNoFeatureAreListedAsUnclassified() {
        assertEquals(List.of("minecraft:ancient_debris", "minecraft:nether_gold_ore", "minecraft:nether_quartz_ore"),
                vanilla().unclassified());
    }

    @Test
    void entriesAreSortedByGroupIdWhateverTheInputOrder() {
        List<FeatureSample> shuffled = new ArrayList<>(VanillaOres.samples());
        Collections.shuffle(shuffled, new Random(42));
        OreTable a = vanilla();
        OreTable b = OreTable.build(shuffled, VanillaOres.ORE_TAG, Overrides.NONE, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
        List<String> ids = a.entries().stream().map(OreTable.Entry::groupId).toList();
        assertEquals(ids.stream().sorted().toList(), ids);
        assertEquals(a.entries(), b.entries(), "the table does not depend on the order features were met in");
    }

    @Test
    void vanillaHasEveryRarity() {
        OreTable table = vanilla();
        assertTrue(table.emptyRarities().isEmpty());
        assertFalse(table.isEmpty());
    }

    @Test
    void anOverrideMovesTheWholeGroup() {
        OreTable table = vanilla(Overrides.parse(List.of("minecraft:deepslate_coal_ore=rare"), VanillaOres.ORE_TAG));
        OreTable.Entry coal = entry(table, "minecraft:coal_ore");
        assertEquals(Rarity.RARE, coal.rarity());
        assertEquals(Rarity.COMMON, coal.classifiedAs());
        assertEquals(1107.6, coal.expectedBlocks(), 1e-6, "an override changes the class, not the numbers");
    }

    /** ADR 0009 step 8: an override on an ore with no feature data gives it a flat profile and its rarity's median weight. */
    @Test
    void anOverrideOnAnOreWithNoFeatureGivesAFlatProfileAndTheMedianWeight() {
        OreTable table = vanilla(Overrides.parse(List.of("minecraft:nether_quartz_ore=common"), VanillaOres.ORE_TAG));
        OreTable.Entry quartz = entry(table, "minecraft:nether_quartz_ore");
        assertEquals(Rarity.COMMON, quartz.rarity());
        assertNull(quartz.classifiedAs());
        assertEquals(entry(table, "minecraft:iron_ore").weight(), quartz.weight(), 1e-9, "median of coal, iron, copper");
        assertEquals(HeightProfile.flat(VanillaOres.FLOOR_Y, VanillaOres.LAYERS), quartz.profile());
        assertEquals("minecraft:nether_quartz_ore", quartz.stoneBlock());
        assertEquals("minecraft:nether_quartz_ore", quartz.deepslateBlock());
        assertEquals(9, table.entries().size());
        assertFalse(table.unclassified().contains("minecraft:nether_quartz_ore"));
    }

    @Test
    void twoOverridesThatDisagreeOnOneOreApplyNeither() {
        OreTable table = vanilla(Overrides.parse(
                List.of("minecraft:iron_ore=rare", "minecraft:deepslate_iron_ore=uncommon"), VanillaOres.ORE_TAG));
        assertEquals(Rarity.COMMON, entry(table, "minecraft:iron_ore").rarity());
        assertEquals(1, table.notes().size(), table.notes().toString());
    }

    /** Epic 4 triage ruling 6 reads this: a pack with no rare ore. */
    @Test
    void aMissingRarityIsReported() {
        List<FeatureSample> noRare = VanillaOres.samples().stream()
                .filter(s -> !s.featureId().contains("diamond") && !s.featureId().contains("emerald")).toList();
        OreTable table = OreTable.build(noRare, VanillaOres.ORE_TAG, Overrides.NONE, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
        assertEquals(EnumSet.of(Rarity.RARE), table.emptyRarities());
        assertTrue(OreTable.needsNormalPreset(table, 54));
    }

    /** Ruling 6: a single-biome overworld (superflat, skyblock) is classified against the normal preset whatever it gives. */
    @Test
    void aSingleBiomeOverworldNeedsTheNormalPreset() {
        assertTrue(OreTable.needsNormalPreset(vanilla(), 1));
        assertTrue(OreTable.needsNormalPreset(vanilla(), 0));
        assertFalse(OreTable.needsNormalPreset(vanilla(), 54));
    }

    @Test
    void anEmptyInputGivesAnEmptyTable() {
        OreTable table = OreTable.build(List.of(), VanillaOres.ORE_TAG, Overrides.NONE, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
        assertTrue(table.isEmpty());
        assertEquals(EnumSet.allOf(Rarity.class), table.emptyRarities());
        assertTrue(table.candidates().isEmpty());
        assertEquals(VanillaOres.ORE_TAG.size(), table.unclassified().size());
    }

    /** ADR 0009 "The ore group's id": the least stone-band block, else the least deepslate-band one. */
    @Test
    void theGroupIdIsTheLeastStoneBandBlockThenTheDeepslateOne() {
        Set<String> tag = Set.of("mod:b_ore", "mod:a_ore", "mod:deep_ore", "mod:z_deep_ore");
        double[] mass = new double[VanillaOres.LAYERS];
        mass[100] = 1;
        FeatureSample two = new FeatureSample("mod:two", List.of(new FeatureSample.Target("mod:b_ore", true, false),
                new FeatureSample.Target("mod:a_ore", true, false), new FeatureSample.Target("mod:z_deep_ore", false, true)),
                10, 5, 1.0, VanillaOres.FLOOR_Y, mass);
        FeatureSample deepOnly = new FeatureSample("mod:deep", List.of(new FeatureSample.Target("mod:deep_ore", false, true)),
                10, 5, 1.0, VanillaOres.FLOOR_Y, mass);
        OreTable table = OreTable.build(List.of(two, deepOnly), tag, Overrides.NONE, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
        OreTable.Entry merged = entry(table, "mod:b_ore");
        assertEquals("mod:a_ore", merged.groupId());
        assertEquals("mod:z_deep_ore", merged.deepslateBlock());
        OreTable.Entry deep = entry(table, "mod:deep_ore");
        assertEquals("mod:deep_ore", deep.groupId());
        assertEquals("mod:deep_ore", deep.stoneBlock(), "a single-variant ore places that block in both bands");
    }

    /** Two features sharing a target join their ores into one group, and a target outside the tag is ignored. */
    @Test
    void featuresSharingATargetAreOneOre() {
        Set<String> tag = Set.of("mod:tin_ore", "mod:deepslate_tin_ore");
        double[] mass = new double[VanillaOres.LAYERS];
        mass[200] = 1;
        FeatureSample stone = new FeatureSample("mod:tin_a", List.of(new FeatureSample.Target("mod:tin_ore", true, false),
                new FeatureSample.Target("minecraft:stone", true, false)), 4, 5, 1.0, VanillaOres.FLOOR_Y, mass);
        FeatureSample both = new FeatureSample("mod:tin_b", List.of(new FeatureSample.Target("mod:tin_ore", true, false),
                new FeatureSample.Target("mod:deepslate_tin_ore", false, true)), 6, 5, 1.0, VanillaOres.FLOOR_Y, mass);
        OreTable table = OreTable.build(List.of(stone, both), tag, Overrides.NONE, VanillaOres.FLOOR_Y, VanillaOres.LAYERS);
        assertEquals(1, table.entries().size());
        OreTable.Entry tin = entry(table, "mod:deepslate_tin_ore");
        assertEquals(50, tin.expectedBlocks(), 1e-9);
        assertEquals(List.of("mod:tin_a", "mod:tin_b"), tin.features());
        assertNull(table.lookup("minecraft:stone"));
    }

    /** A feature whose every origin falls outside the overworld places nothing and is not usable (step 9). */
    @Test
    void aFeatureThatPlacesNothingIsNotUsable() {
        FeatureSample empty = new FeatureSample("mod:none", List.of(new FeatureSample.Target("mod:tin_ore", true, false)),
                0, 5, 1.0, VanillaOres.FLOOR_Y, new double[VanillaOres.LAYERS]);
        OreTable table = OreTable.build(List.of(empty), Set.of("mod:tin_ore"), Overrides.NONE, VanillaOres.FLOOR_Y,
                VanillaOres.LAYERS);
        assertNull(table.lookup("mod:tin_ore"));
        assertEquals(List.of("mod:tin_ore"), table.unclassified());
    }

    @Test
    void aSampleOverAnotherRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> OreTable.build(VanillaOres.samples(), VanillaOres.ORE_TAG,
                Overrides.NONE, 0, VanillaOres.LAYERS));
    }

    @Test
    void theTableIsImmutable() {
        OreTable table = vanilla();
        assertThrows(UnsupportedOperationException.class, () -> table.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> entry(table, "minecraft:iron_ore").features().clear());
        assertThrows(UnsupportedOperationException.class, () -> table.candidates().clear());
    }
}
