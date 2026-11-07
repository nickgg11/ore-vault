package com.orevault.orevault.worldgen;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.ChunkSeed;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.VaultVariant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A team's Vault terrain (§3.1, §11 Dynamic Chunk Generator): the layer stack, flat, with no ore until T4.
 *
 * <p>{@link #fillFromNoise} runs on a background executor (rule 1). Everything it touches is final and immutable, and the
 * only team state it reads is the {@link GenSnapshot}, once a chunk, so one chunk cannot straddle a purchase (ADR 0003).
 * The constructor takes that supplier and the team id and nothing else of the team's, and ArchUnit holds this package away
 * from {@code data} and {@code team} (S15). Built per team in code, never parsed from a pack, so the codec is a unit of the
 * instance.
 *
 * <p>All Vault terrain is written in that one method. The two later steps that could add to it, biome decoration and
 * structures, do nothing here, and they are also what would read the world seed. So a chunk's seed comes only from
 * {@link #chunkSeed}, and within one generation of the Vault a chunk comes out the same whenever it is first visited.
 * {@code createReferences} stays as it is: with no structure starts it has nothing to reference.
 */
public final class VaultChunkGenerator extends ChunkGenerator {
    public static final ResourceKey<Biome> BIOME =
            ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "vault"));

    private static final int NO_WATER = 0;

    private final UUID teamId;
    private final VaultVariant variant;
    private final BlockState[] column;
    private final int firstFreeY;
    private final Supplier<GenSnapshot> snapshots;

    /** On the main thread: this reads the block registry, so the generation thread never has to. */
    public VaultChunkGenerator(UUID teamId, Holder<Biome> biome, VaultVariant variant, LayerStack layers, Supplier<GenSnapshot> snapshots) {
        super(new FixedBiomeSource(biome));
        this.teamId = teamId;
        this.variant = variant;
        this.snapshots = snapshots;
        this.column = layers.column().stream()
                .map(id -> BuiltInRegistries.BLOCK.getValue(Identifier.parse(id)).defaultBlockState())
                .toArray(BlockState[]::new);
        if (column.length != variant.height()) {
            throw new IllegalArgumentException("A stack of " + column.length + " for " + variant.id());
        }
        this.firstFreeY = variant.minY() + firstAirIndex(column);
    }

    /** From the resolved states: a layer file may say {@code air} or a modded air, and only the block can tell. */
    private static int firstAirIndex(BlockState[] column) {
        for (int index = 0; index < column.length; index++) {
            if (column[index].isAir()) {
                return index;
            }
        }
        return column.length;
    }

    /** The seed for one feature in one chunk: each feature passes its own salt, so adding one shifts no other (§3.1). */
    public long chunkSeed(GenSnapshot snapshot, ChunkPos chunk, long salt) {
        return ChunkSeed.of(teamId, snapshot.vaultGeneration(), chunk.x(), chunk.z(), salt);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        GenSnapshot snapshot = snapshots.get();
        if (snapshot.variant() != variant) {
            throw new IllegalStateException("Generator for " + variant.id() + " handed a snapshot of " + snapshot.variant().id());
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for (int index = 0; index < column.length; index++) {
            BlockState state = column[index];
            if (state.isAir()) {
                continue;
            }
            int y = variant.minY() + index;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(pos.set(x, y, z), state);
                    oceanFloor.update(x, y, z, state);
                    worldSurface.update(x, y, z, state);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    /** §3.1: natural spawning asks the generator, and a biome modifier using {@code neoforge:any} can fill the biome's lists. */
    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory category, BlockPos pos) {
        return MobSpawnSettings.EMPTY_MOB_LIST;
    }

    /** The chunk-generation mob pass, the one path that reads the biome's list directly (ADR 0003). */
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    /**
     * Does nothing. A biome modifier on {@code neoforge:any} reaches {@code orevault:vault} and adds its placed features to
     * the biome, and the base method would place them: ore by static feature, against §3.1, outside the baseline's counts
     * and the vein index, seeded from the world seed (ADR 0003). Heightmap priming and border ticks are in the caller.
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    /** Does nothing, for the same reason: a structure whose biome set reaches the Vault would be terrain from outside this class. */
    @Override
    public void createStructures(
            RegistryAccess registryAccess, ChunkGeneratorStructureState state, StructureManager structureManager, ChunkAccess chunk,
            StructureTemplateManager templates, ResourceKey<Level> level) {
    }

    /** No caves by default (§3.1). */
    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
    }

    /** {@link #fillFromNoise} writes the surface. */
    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {
    }

    @Override
    public int getGenDepth() {
        return variant.height();
    }

    @Override
    public int getSeaLevel() {
        return NO_WATER;
    }

    @Override
    public int getMinY() {
        return variant.minY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return firstFreeY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return new NoiseColumn(variant.minY(), column.clone());
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        result.add("Ore Vault team " + teamId);
    }
}
