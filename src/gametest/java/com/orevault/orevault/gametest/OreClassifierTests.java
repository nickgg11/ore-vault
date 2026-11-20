package com.orevault.orevault.gametest;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.core.ore.Overrides;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.ore.ClassifiedOres;
import com.orevault.orevault.ore.OreClassifier;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

/**
 * ADR 0009's classifier gametest: the real classifier on the {@code minecraft:normal} preset's overworld, read from this
 * server's registries, so the features come through NeoForge's biome modifiers as they would in a pack (Epic 4 triage,
 * DEV-E4-3, QA-E4-4). A class can come out right from the wrong features, and the old bug was in reading the features, not
 * in the rule (QA-27), so it checks the expected blocks and the features used, not only the eight classes.
 */
final class OreClassifierTests {
    /** Research section 2's expected blocks a chunk over the features step 3 uses, solid rock, Y-64..319. */
    private static final Map<String, Double> EXPECTED_BLOCKS = new TreeMap<>(Map.of(
            "minecraft:coal_ore", 1107.6, "minecraft:iron_ore", 645.6, "minecraft:copper_ore", 134.3,
            "minecraft:gold_ore", 30.3, "minecraft:redstone_ore", 43.8, "minecraft:lapis_ore", 29.5,
            "minecraft:diamond_ore", 29.6, "minecraft:emerald_ore", 36.1));
    private static final Map<String, Rarity> RARITY = new TreeMap<>(Map.of(
            "minecraft:coal_ore", Rarity.COMMON, "minecraft:iron_ore", Rarity.COMMON, "minecraft:copper_ore", Rarity.COMMON,
            "minecraft:gold_ore", Rarity.UNCOMMON, "minecraft:redstone_ore", Rarity.UNCOMMON,
            "minecraft:lapis_ore", Rarity.UNCOMMON, "minecraft:diamond_ore", Rarity.RARE,
            "minecraft:emerald_ore", Rarity.RARE));
    /** Emerald's one feature is listed by the ten mountain biomes, about 0.18 of the overworld's (ADR 0009, BAL-2). */
    private static final double EMERALD_SHARE = 0.18;

    private OreClassifierTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static void within(GameTestHelper helper, String what, double expected, double actual, double relative) {
        if (!(Math.abs(actual - expected) <= relative * expected)) {
            helper.fail(Component.literal(what + ": " + actual + " is not within " + Math.round(relative * 100) + "% of "
                    + expected));
        }
    }

    private static OreTable.Entry entry(GameTestHelper helper, OreTable table, String block) {
        OreTable.Entry entry = table.lookup(block);
        if (entry == null) {
            helper.fail(Component.literal(block + " has no entry; classified: " + table.entries().stream()
                    .map(OreTable.Entry::groupId).toList()));
            throw new IllegalStateException("unreachable");
        }
        return entry;
    }

    private static OreTable classifyNormal(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        Optional<LevelStem> normal = OreClassifier.normalOverworld(access);
        if (normal.isEmpty()) {
            helper.fail(Component.literal("no minecraft:normal overworld in this server's registries"));
            throw new IllegalStateException("unreachable");
        }
        DimensionType type = normal.get().type().value();
        return OreClassifier.classify(access, normal.get().generator(),
                LevelHeightAccessor.create(type.minY(), type.height()), Overrides.NONE);
    }

    /** The eight classes, each ore's expected blocks within 10%, and lapis's margin on its own (ADR 0009 Tests). */
    static void classifiesTheEightVanillaOres(GameTestHelper helper) {
        OreTable table = classifyNormal(helper);
        check(helper, "classified ores", RARITY.keySet().stream().toList(),
                table.entries().stream().map(OreTable.Entry::groupId).toList());
        RARITY.forEach((ore, rarity) -> check(helper, ore, rarity, entry(helper, table, ore).rarity()));
        EXPECTED_BLOCKS.forEach((ore, expected) ->
                within(helper, ore + " expected blocks", expected, entry(helper, table, ore).expectedBlocks(), 0.10));
        OreTable.Entry lapis = entry(helper, table, "minecraft:lapis_ore");
        within(helper, "lapis blocks a vein", 4.97, lapis.meanBlocksPerVein(), 0.05);
        check(helper, "lapis clears 4.5", true, lapis.meanBlocksPerVein() >= 4.5);
        check(helper, "every rarity", List.of(), List.copyOf(table.emptyRarities()));
        helper.succeed();
    }

    /** Step 3: copper without its dripstone-only large feature, gold without badlands' extra, emerald with its mountain one. */
    static void usesTheRightFeatures(GameTestHelper helper) {
        OreTable table = classifyNormal(helper);
        check(helper, "copper", List.of("minecraft:ore_copper"), entry(helper, table, "minecraft:copper_ore").features());
        check(helper, "gold", List.of("minecraft:ore_gold", "minecraft:ore_gold_lower"),
                entry(helper, table, "minecraft:gold_ore").features());
        check(helper, "coal", List.of("minecraft:ore_coal_lower", "minecraft:ore_coal_upper"),
                entry(helper, table, "minecraft:coal_ore").features());
        check(helper, "iron", List.of("minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_iron_upper"),
                entry(helper, table, "minecraft:iron_ore").features());
        check(helper, "diamond", List.of("minecraft:ore_diamond", "minecraft:ore_diamond_buried",
                "minecraft:ore_diamond_large", "minecraft:ore_diamond_medium"),
                entry(helper, table, "minecraft:diamond_ore").features());
        check(helper, "emerald", List.of("minecraft:ore_emerald"), entry(helper, table, "minecraft:emerald_ore").features());
        OreTable.Entry emerald = entry(helper, table, "minecraft:emerald_ore");
        within(helper, "emerald's biome share", EMERALD_SHARE, emerald.weight() / emerald.expectedBlocks(), 0.10);
        OreTable.Entry coal = entry(helper, table, "minecraft:coal_ore");
        within(helper, "coal weighs its blocks", coal.expectedBlocks(), coal.weight(), 1e-9);
        helper.succeed();
    }

    /** Old PR #117: no default rarity. Ancient debris has no overworld feature and is never placed (§3.1, step 9). */
    static void nonOresAndAncientDebrisHaveNoEntry(GameTestHelper helper) {
        OreTable table = classifyNormal(helper);
        for (String block : List.of("minecraft:stone", "minecraft:deepslate", "minecraft:gravel", "minecraft:clay",
                "minecraft:ancient_debris")) {
            check(helper, block, null, table.lookup(block));
        }
        check(helper, "ancient debris is unclassified", true, table.unclassified().contains("minecraft:ancient_debris"));
        check(helper, "ancient debris is not a pick", false, table.candidates().stream()
                .anyMatch(candidate -> candidate.groupId().equals("minecraft:ancient_debris")));
        OreTable.Entry diamond = entry(helper, table, "minecraft:deepslate_diamond_ore");
        check(helper, "deepslate diamond's group", "minecraft:diamond_ore", diamond.groupId());
        check(helper, "deepslate band block", "minecraft:deepslate_diamond_ore", diamond.deepslateBlock());
        helper.succeed();
    }

    /** Two runs give the same table: per-feature seeds and sorted iteration (DEV-E4-5, RV-E4-6). */
    static void twoRunsGiveTheSameTable(GameTestHelper helper) {
        check(helper, "entries", classifyNormal(helper).entries(), classifyNormal(helper).entries());
        helper.succeed();
    }

    /**
     * The start listener published before this test ran, so before any Vault could be restored, with pickers for both
     * types. On a single-biome overworld (the gametest server's, if it is flat) the source is the normal preset (ruling 6).
     */
    static void publishedAtStartWithBothTypesPickers(GameTestHelper helper) {
        ClassifiedOres published = OreClassifier.published();
        if (published == null) {
            helper.fail(Component.literal("nothing published at server start"));
            return;
        }
        MinecraftServer server = helper.getLevel().getServer();
        int biomes = server.overworld().getChunkSource().getGenerator().getBiomeSource().possibleBiomes().size();
        if (biomes <= 1) {
            check(helper, "source", OreClassifier.NORMAL_SOURCE, published.source());
        }
        check(helper, "no stone", null, published.table().lookup("minecraft:stone"));
        Map<Band, ?> base = published.pickers(VaultVariant.BASE);
        Map<Band, ?> expanded = published.pickers(VaultVariant.EXPANDED);
        check(helper, "base bands", List.of(Band.STONE), base == null ? null : List.copyOf(base.keySet()));
        check(helper, "expanded bands", List.of(Band.STONE, Band.DEEPSLATE),
                expanded == null ? null : List.copyOf(expanded.keySet()));
        helper.succeed();
    }
}
