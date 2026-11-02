package com.orevault.orevault.gametest;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.ChunkSeed;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.vault.VaultLayerConfig;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import java.lang.reflect.Method;
import java.util.List;
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
        return new VaultChunkGenerator(TEAM, vaultBiome(level), variant, LayerStack.builtIn(variant), snapshots);
    }

    private static VaultChunkGenerator generator(ServerLevel level, VaultVariant variant) {
        return generator(level, variant, () -> new GenSnapshot(variant, 0));
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

    /** Every block of the chunk against the stack, so an ore anywhere fails it (§3.1, no ore until T4). */
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

    /** ADR 0003: one supplier call a chunk, so no chunk can straddle a purchase. */
    static void readsTheSnapshotOnceAChunk(GameTestHelper helper) {
        AtomicInteger reads = new AtomicInteger();
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE, () -> {
            reads.incrementAndGet();
            return new GenSnapshot(VaultVariant.BASE, 0);
        });
        check(helper, "reads building the generator", 0, reads.get());
        filled(helper, generator, VaultVariant.BASE);
        check(helper, "reads for one chunk", 1, reads.get());
        helper.succeed();
    }

    /** ADR 0003: the generator is built per variant and asserts that the snapshot agrees. */
    static void refusesASnapshotOfTheOtherVariant(GameTestHelper helper) {
        VaultChunkGenerator generator = generator(helper.getLevel(), VaultVariant.BASE, () -> new GenSnapshot(VaultVariant.EXPANDED, 0));
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

    /** The team, the reset count, the chunk and a salt, and no way for a world seed to get in (§3.1). */
    static void theSeedComesFromTheTeamAndTheChunk(GameTestHelper helper) {
        GenSnapshot snapshot = new GenSnapshot(VaultVariant.BASE, 4);
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
