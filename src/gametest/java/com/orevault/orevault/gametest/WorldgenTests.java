package com.orevault.orevault.gametest;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.BandBounds;
import com.orevault.orevault.core.gen.BlobPlacer;
import com.orevault.orevault.core.gen.ChunkPlan;
import com.orevault.orevault.core.gen.ChunkSeed;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.GenerationTiers;
import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.OreBaseline;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.gen.VeinExtent;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.ore.ClassifiedOres;
import com.orevault.orevault.worldgen.OreInputs;
import com.orevault.orevault.vault.VaultLayerConfig;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/**
 * The generator and its data files in a real server, on a {@code ProtoChunk}. No Vault level exists to run it in until
 * E3-S6a, so what the chunk pipeline does with it, lighting above all, is a client playtest step.
 */
final class WorldgenTests {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final int SURFACE_TOP = 251;

    private WorldgenTests() {
    }

    private static void check(GameTestHelper helper, String what, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            helper.fail(Component.literal(what + ": expected " + expected + ", got " + actual));
        }
    }

    private static Holder<Biome> vaultBiome(ServerLevel level) {
        return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(VaultChunkGenerator.BIOME);
    }

    private static VaultChunkGenerator generator(ServerLevel level, VaultVariant variant, Supplier<GenSnapshot> snapshots) {
        return new VaultChunkGenerator(TEAM, vaultBiome(level), variant, LayerStack.builtIn(variant), snapshots, OreInputs.EMPTY);
    }

    private static VaultChunkGenerator generator(ServerLevel level, VaultVariant variant) {
        return generator(level, variant, () -> GenSnapshot.of(variant, 0, GenerationTiers.NONE));
    }

    private static ProtoChunk emptyChunk(ServerLevel level, VaultVariant variant) {
        return new ProtoChunk(new ChunkPos(3, -2), UpgradeData.EMPTY, LevelHeightAccessor.create(variant.minY(), variant.height()),
                level.palettedContainerFactory(), null);
    }

    private static ProtoChunk filled(GameTestHelper helper, VaultChunkGenerator generator, VaultVariant variant) {
        return generator.fillFromNoise(null, null, null, emptyChunk(helper.getLevel(), variant)).join() instanceof ProtoChunk chunk
                ? chunk : null;
    }

    private static String idOf(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Every block of the chunk against the stack, so an ore anywhere fails it: {@link OreInputs#EMPTY} places nothing. */
    private static void fillsTheWholeStack(GameTestHelper helper, VaultVariant variant) {
        ProtoChunk chunk = filled(helper, generator(helper.getLevel(), variant), variant);
        List<String> column = LayerStack.builtIn(variant).column();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int index = 0; index < column.size(); index++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(x, variant.minY() + index, z);
                    String found = idOf(chunk.getBlockState(pos));
                    if (!found.equals(column.get(index))) {
                        helper.fail(Component.literal(pos.toShortString() + ": expected " + column.get(index) + ", got " + found));
                    }
                }
            }
        }
        for (Heightmap.Types type : List.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)) {
            check(helper, type.name(), SURFACE_TOP, chunk.getOrCreateHeightmapUnprimed(type).getFirstAvailable(5, 9));
        }
        helper.succeed();
    }

    static void fillsTheBaseStack(GameTestHelper helper) {
        fillsTheWholeStack(helper, VaultVariant.BASE);
    }

    static void fillsTheExpandedStack(GameTestHelper helper) {
        fillsTheWholeStack(helper, VaultVariant.EXPANDED);
    }

    /**
     * QA-E4-5, RV-E4-3.3: the generator writes exactly the plan. A fixed chunk of a Vault built the way every Vault is, from
     * the published table, against the pure plan computed here from the same table, team, snapshot and chunk: every
     * position of every vein holds its ore in its band's block, every gravel and clay position holds gravel or clay, every
     * other band position is the filler, every layer outside the bands is the stack, and the chunk's ore count is the
     * plan's. A generator that used another seed, salt, band block or variant fails it; so does one that skipped a bit.
     */
    static void writesEveryPlannedBlock(GameTestHelper helper) {
        ClassifiedOres published = VaultChunkGenerator.publishedOres();
        Map<String, String[]> blocks = new HashMap<>();
        Set<String> oreIds = new HashSet<>();
        for (OreTable.Entry entry : published.table().entries()) {
            blocks.put(entry.groupId(), new String[] {entry.stoneBlock(), entry.deepslateBlock()});
            oreIds.add(entry.stoneBlock());
            oreIds.add(entry.deepslateBlock());
        }
        for (VaultVariant variant : VaultVariant.values()) {
            GenSnapshot snapshot = GenSnapshot.of(variant, 1, GenerationTiers.NONE);
            LayerStack stack = LayerStack.builtIn(variant);
            VaultChunkGenerator generator = VaultChunkGenerator.forVault(TEAM, vaultBiome(helper.getLevel()), variant, stack, () -> snapshot);
            ProtoChunk chunk = filled(helper, generator, variant);
            ChunkPlan plan = ChunkPlan.of(OreBaseline.BASE, variant, published.pickers(variant), snapshot, TEAM, 3, -2);
            check(helper, variant.id() + " plan has ore", true, plan.oreBlocks() > 0);
            check(helper, variant.id() + " filled this session", true, generator.filledThisSession(3, -2));

            Map<Long, String> expected = new HashMap<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (VeinExtent vein : plan.ore().veins()) {
                String block = blocks.get(vein.oreGroup())[vein.band() == Band.STONE ? 0 : 1];
                for (int y = 0; y < vein.sizeY(); y++) {
                    for (int z = 0; z < vein.sizeZ(); z++) {
                        for (int x = 0; x < vein.sizeX(); x++) {
                            if (vein.isSet(x, y, z)) {
                                expected.put(pos.set(vein.minX() + x, vein.minY() + y, vein.minZ() + z).asLong(), block);
                            }
                        }
                    }
                }
            }
            for (int cell : plan.blobs().gravel()) {
                expected.put(pos.set(BlobPlacer.x(cell), BlobPlacer.y(variant, cell), BlobPlacer.z(cell)).asLong(), "minecraft:gravel");
            }
            for (int cell : plan.blobs().clay()) {
                expected.put(pos.set(BlobPlacer.x(cell), BlobPlacer.y(variant, cell), BlobPlacer.z(cell)).asLong(), "minecraft:clay");
            }

            List<String> column = stack.column();
            long ore = 0;
            for (int index = 0; index < column.size(); index++) {
                int y = variant.minY() + index;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        pos.set(x, y, z);
                        String want = expected.getOrDefault(pos.asLong(), column.get(index));
                        String found = idOf(chunk.getBlockState(pos));
                        if (!found.equals(want)) {
                            helper.fail(Component.literal(variant.id() + " " + pos.toShortString() + ": expected " + want + ", got " + found));
                            return;
                        }
                        ore += oreIds.contains(found) ? 1 : 0;
                        if (expected.containsKey(pos.asLong()) && !inABand(variant, y)) {
                            helper.fail(Component.literal(variant.id() + " planned a block outside the bands at " + pos.toShortString()));
                            return;
                        }
                    }
                }
            }
            check(helper, variant.id() + " ore blocks", plan.oreBlocks(), ore);
            for (Heightmap.Types type : List.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)) {
                check(helper, variant.id() + " " + type.name(), SURFACE_TOP, chunk.getOrCreateHeightmapUnprimed(type).getFirstAvailable(5, 9));
            }
        }
        helper.succeed();
    }

    private static boolean inABand(VaultVariant variant, int y) {
        for (Band band : Band.values()) {
            BandBounds bounds = variant.band(band).orElse(null);
            if (bounds != null && y >= bounds.bottomY() && y <= bounds.topY()) {
                return true;
            }
        }
        return false;
    }

    /** ARCH-E4-8: with ore to place, a band that is not one solid filler is refused at construction, not written into. */
    static void refusesABandThatIsNotOneFiller(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultVariant base = VaultVariant.BASE;
        LayerStack shortAir = new LayerStack(List.of(new LayerStack.Layer("minecraft:stone", 100), new LayerStack.Layer("air", 220)));
        try {
            VaultChunkGenerator.forVault(TEAM, vaultBiome(level), base, shortAir, () -> GenSnapshot.of(base, 0, GenerationTiers.NONE));
        } catch (IllegalArgumentException e) {
            helper.succeed();
            return;
        }
        helper.fail(Component.literal("a generator with ore to place took a stone band with air in it"));
    }

    /**
     * Mutation 15 of the S3 review: a hand-built stack whose band is an air other than {@code minecraft:air} passes the
     * string check, and only the constructor's resolved-state check refuses it. (A layer file like it falls back in
     * {@code VaultLayerConfig}, S3-RV-2.)
     */
    static void refusesABandOfAnotherAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultVariant base = VaultVariant.BASE;
        LayerStack caveAir = new LayerStack(List.of(new LayerStack.Layer("minecraft:bedrock", 1),
                new LayerStack.Layer("minecraft:cave_air", 245), new LayerStack.Layer("minecraft:dirt", 4),
                new LayerStack.Layer("minecraft:grass_block", 1), new LayerStack.Layer("minecraft:air", 69)));
        check(helper, "the string check passes it", java.util.Optional.empty(), caveAir.bandFillerProblem(base));
        try {
            VaultChunkGenerator.forVault(TEAM, vaultBiome(level), base, caveAir, () -> GenSnapshot.of(base, 0, GenerationTiers.NONE));
        } catch (IllegalArgumentException e) {
            helper.succeed();
            return;
        }
        helper.fail(Component.literal("a generator with ore to place took a stone band of cave air"));
    }

    /** ADR 0003: one supplier call a chunk, so no chunk can straddle a purchase. */
    static void readsTheSnapshotOnceAChunk(GameTestHelper helper) {
        AtomicInteger reads = new AtomicInteger();
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE, () -> {
            reads.incrementAndGet();
            return GenSnapshot.of(VaultVariant.BASE, 0, GenerationTiers.NONE);
        });
        check(helper, "reads building the generator", 0, reads.get());
        filled(helper, generator, VaultVariant.BASE);
        check(helper, "reads for one chunk", 1, reads.get());
        helper.succeed();
    }

    /** ADR 0003: the generator is built per variant and asserts that the snapshot agrees. */
    static void refusesASnapshotOfTheOtherVariant(GameTestHelper helper) {
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE, () -> GenSnapshot.of(VaultVariant.EXPANDED, 0, GenerationTiers.NONE));
        try {
            filled(helper, generator, VaultVariant.BASE);
        } catch (RuntimeException e) {
            helper.succeed();
            return;
        }
        helper.fail(Component.literal("a chunk was generated under the other variant"));
    }

    /** The plains biome has spawners: an answer read from the biome, as the default {@code getMobsAt} reads it, fails this. */
    static void anyBiomesSpawnsAreRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultChunkGenerator generator = generator(level, VaultVariant.BASE);
        Holder<Biome> plains = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        for (MobCategory category : MobCategory.values()) {
            check(helper, category.name(), true, generator.getMobsAt(plains, null, category, BlockPos.ZERO).isEmpty());
            check(helper, category.name(), true, generator.getMobsAt(vaultBiome(level), null, category, BlockPos.ZERO).isEmpty());
        }
        generator.spawnOriginalMobs(null);
        helper.succeed();
    }

    /**
     * ADR 0003: a modifier on {@code neoforge:any} reaches the biome's features, so nothing but {@code fillFromNoise} may
     * write Vault terrain. The base methods dereference these nulls, so only a method that does nothing passes.
     */
    static void placesNoFeaturesAndNoStructures(GameTestHelper helper) {
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE);
        generator.applyBiomeDecoration(null, null, null);
        generator.createStructures(null, null, null, null, null, null);
        helper.succeed();
    }

    /** {@code air} is the block {@code minecraft:air} and passes validation, so the first free block comes from the state. */
    static void theFirstFreeBlockComesFromTheResolvedState(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultVariant base = VaultVariant.BASE;
        LevelHeightAccessor heights = LevelHeightAccessor.create(base.minY(), base.height());
        LayerStack shortAir = new LayerStack(List.of(new LayerStack.Layer("minecraft:stone", 100), new LayerStack.Layer("air", 220)));
        VaultChunkGenerator generator = new VaultChunkGenerator(TEAM, vaultBiome(level), base, shortAir, () -> GenSnapshot.of(base, 0, GenerationTiers.NONE),
                OreInputs.EMPTY);
        check(helper, "short air id", 100, generator.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE_WG, heights, null));

        LayerStack solid = new LayerStack(List.of(new LayerStack.Layer("minecraft:stone", 320)));
        VaultChunkGenerator noAir = new VaultChunkGenerator(TEAM, vaultBiome(level), base, solid, () -> GenSnapshot.of(base, 0, GenerationTiers.NONE),
                OreInputs.EMPTY);
        check(helper, "no air", 320, noAir.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE_WG, heights, null));
        helper.succeed();
    }

    /** The team, the reset count, the chunk and a salt, and no way for a world seed to get in (§3.1). */
    static void theSeedComesFromTheTeamAndTheChunk(GameTestHelper helper) {
        GenSnapshot snapshot = GenSnapshot.of(VaultVariant.BASE, 4, GenerationTiers.NONE);
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE, () -> snapshot);
        ChunkPos pos = new ChunkPos(3, -2);
        check(helper, "seed", ChunkSeed.of(TEAM, 4, 3, -2, 7L), generator.chunkSeed(snapshot, pos, 7L));
        helper.succeed();
    }

    static void answersTheHeightQuestions(GameTestHelper helper) {
        for (VaultVariant variant : VaultVariant.values()) {
            VaultChunkGenerator generator = generator(helper.getLevel(), variant);
            LevelHeightAccessor heights = LevelHeightAccessor.create(variant.minY(), variant.height());
            check(helper, "min y", variant.minY(), generator.getMinY());
            check(helper, "depth", variant.height(), generator.getGenDepth());
            check(helper, "base height", SURFACE_TOP, generator.getBaseHeight(0, 0, Heightmap.Types.WORLD_SURFACE_WG, heights, null));
            NoiseColumn column = generator.getBaseColumn(0, 0, heights, null);
            check(helper, "grass", "minecraft:grass_block", idOf(column.getBlock(250)));
            check(helper, "air", "minecraft:air", idOf(column.getBlock(SURFACE_TOP)));
        }
        helper.succeed();
    }

    /** ADR 0003: built in code per team, never parsed, so the codec is a unit of the instance. */
    static void theCodecIsAUnitOfTheGenerator(GameTestHelper helper) throws ReflectiveOperationException {
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE);
        Method codec = ChunkGenerator.class.getDeclaredMethod("codec");
        codec.setAccessible(true);
        var decoded = ((com.mojang.serialization.MapCodec<?>) codec.invoke(generator)).codec()
                .parse(com.mojang.serialization.JavaOps.INSTANCE, java.util.Map.of()).getOrThrow();
        check(helper, "decoded", generator, decoded);
        helper.succeed();
    }

    /** That the game's own codec took both files, and that §3.1's table is what it holds. */
    static void theDimensionTypesLoadWithTheHeightsInJava(GameTestHelper helper) {
        HolderLookup.RegistryLookup<DimensionType> types = helper.getLevel().registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE);
        for (VaultVariant variant : VaultVariant.values()) {
            ResourceKey<DimensionType> key = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(variant.id()));
            DimensionType type = types.getOrThrow(key).value();
            check(helper, "min_y", variant.minY(), type.minY());
            check(helper, "height", variant.height(), type.height());
            check(helper, "logical_height", variant.height(), type.logicalHeight());
            check(helper, "has_fixed_time", true, type.hasFixedTime());
            check(helper, "has_skylight", true, type.hasSkyLight());
            check(helper, "ambient_light", 1.0F, type.ambientLight());
            check(helper, "attributes", 7, type.attributes().keySet().size());
        }
        helper.succeed();
    }

    static void theBiomeLoadsWithNoSpawnsAndInNoBroadTag(GameTestHelper helper) {
        Holder<Biome> biome = vaultBiome(helper.getLevel());
        for (MobCategory category : MobCategory.values()) {
            check(helper, category.name(), true, biome.value().getMobSettings().getMobs(category).isEmpty());
        }
        check(helper, "#minecraft:is_overworld", false, biome.is(BiomeTags.IS_OVERWORLD));
        check(helper, "#c:is_overworld", false, biome.is(Tags.Biomes.IS_OVERWORLD));
        check(helper, "attributes", 2, biome.value().getAttributes().keySet().size());
        helper.succeed();
    }

    /** The files as the game finds them; a missing path would fall back in silence but for the first check. */
    static void theLayerFilesLoadFromTheDataPack(GameTestHelper helper) {
        for (VaultVariant variant : VaultVariant.values()) {
            Identifier file = VaultLayerConfig.file(variant);
            check(helper, "namespace", OreVault.MOD_ID, file.getNamespace());
            check(helper, "file", true, helper.getLevel().getServer().getResourceManager().getResource(file).isPresent());
            check(helper, "stack", LayerStack.builtIn(variant), VaultLayerConfig.load(helper.getLevel().getServer(), variant));
        }
        helper.succeed();
    }
}
