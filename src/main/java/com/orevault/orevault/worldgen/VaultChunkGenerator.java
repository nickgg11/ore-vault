package com.orevault.orevault.worldgen;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Custom chunk generator for team Vault dimensions (§3.1, §11): an open air
 * layer at the top (64 blocks), then solid stone below with a deepslate
 * "surface" at the top of the stone layer — no aquifers, caves, or
 * structures. Ore placement is driven by the team's skill state.
 *
 * <p>The generator is created per team by {@code VaultDimensions} and reads a
 * main-thread-maintained {@link SkillSnapshot} at generation time so node
 * purchases affect newly generated chunks without a restart (§11). Node-driven
 * placement math is a documented hook — {@code [44]}/{@code [45]} implement it;
 * until then a single hardcoded coal vein is placed per chunk.</p>
 *
 * <p>The 40% stone floor (§3.1) is enforced by capping the ore budget at 60%
 * of the chunk's stone volume; no skill state can ever push stone below 40%.</p>
 */
public final class VaultChunkGenerator extends ChunkGenerator {

    /** Top layers generated as deepslate — the vault's "surface" (§3.1). */
    public static final int DEEPSLATE_SURFACE_LAYERS = 8;
    /** Open air layer at the top of the dimension, in blocks (§3.1 revision). */
    public static final int OPEN_AIR_LAYER = 64;
    /** Hard 40% stone floor: ores may never exceed 60% of a chunk's stone volume (§3.1). */
    public static final double MAX_ORE_FRACTION = 0.60;
    /** Placeholder vein parameters until node-driven placement lands in [44]/[45]. */
    private static final int PLACEHOLDER_VEINS = 2;
    private static final int PLACEHOLDER_VEIN_SIZE = 4;

    private final UUID teamId;
    private final int minY;
    private final int height;
    private final Supplier<SkillSnapshot> skills;
    private final AtomicBoolean loggedSkillState = new AtomicBoolean();

    public VaultChunkGenerator(UUID teamId, Holder<Biome> biome, int minY, int height, Supplier<SkillSnapshot> skills) {
        super(new FixedBiomeSource(biome));
        this.teamId = teamId;
        this.minY = minY;
        this.height = height;
        this.skills = skills;
    }

    /** Team id this generator was created for. */
    public UUID teamId() {
        return teamId;
    }

    /**
     * Immutable, thread-safe snapshot of the team's skill state, maintained on
     * the main thread by {@code VaultDimensions} and read at chunk generation
     * time (which runs on a background executor).
     */
    public record SkillSnapshot(Set<String> resonanceNodes, Set<String> animusNodes, int totalSkillPointsInvested) {

        public static final SkillSnapshot EMPTY = new SkillSnapshot(Set.of(), Set.of(), 0);

        /** Builds a snapshot from the team's SavedData (main thread only). */
        public static SkillSnapshot of(OreVaultTeamData data) {
            return new SkillSnapshot(
                    data.resonanceTree().getUnlockedTiers().keySet(),
                    data.animusTree().getUnlockedTiers().keySet(),
                    data.resonanceTree().skillPointsInvested() + data.animusTree().skillPointsInvested()
            );
        }

        public boolean isEmpty() {
            return totalSkillPointsInvested == 0;
        }
    }

    // ----- ChunkGenerator plumbing -----

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        // Constructed in code only (one per team); never parsed from datapack JSON.
        return MapCodec.unit(this);
    }

    @Override
    public void applyCarvers(
            WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk
    ) {
        // No carvers (§3.1: no caves by default).
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState randomState, ChunkAccess protoChunk) {
        // The surface layer is written directly by fillFromNoise; no surface rules apply.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        // The vault biome defines no spawn entries (§3.1: natural mob spawning disabled).
    }

    @Override
    public int getGenDepth() {
        return height;
    }

    @Override
    public int getSeaLevel() {
        return 0; // no water bodies
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        // First free space: one block above the deepslate surface (§3.1 air layer).
        return minY + height - OPEN_AIR_LAYER;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        BlockState[] states = new BlockState[height];
        int stoneTop = minY + height - OPEN_AIR_LAYER;
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < height; y++) {
            int worldY = minY + y;
            if (worldY >= stoneTop) {
                states[y] = air;
            } else {
                states[y] = stoneTop - worldY <= DEEPSLATE_SURFACE_LAYERS ? deepslate : stone;
            }
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        result.add("Ore Vault — team " + teamId);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk
    ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int maxY = minY + height - 1;
        int stoneTop = minY + height - OPEN_AIR_LAYER;
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = minY; y <= maxY; y++) {
            BlockState state;
            if (y >= stoneTop) {
                state = air;
            } else {
                state = stoneTop - y <= DEEPSLATE_SURFACE_LAYERS ? deepslate : stone;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.setBlockState(pos.set(x, y, z), state);
                    if (!state.isAir()) {
                        oceanFloor.update(x, y, z, state);
                        worldSurface.update(x, y, z, state);
                    }
                }
            }
        }

        placeOres(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    // ----- ore placement -----

    /**
     * Places ores for the chunk. Reads the team's skill state at generation
     * time; node modifiers are a TODO hook for [44]/[45], so until then only a
     * small hardcoded coal vein is placed. The ore budget enforces the 40%
     * stone-content floor regardless of any future node math.
     */
    private void placeOres(ChunkAccess chunk) {
        SkillSnapshot snapshot = skills.get();
        if (loggedSkillState.compareAndSet(false, true) && !snapshot.isEmpty()) {
            OreVault.LOGGER.debug(
                    "Vault chunk gen: team {} has {} skill points invested; node modifiers not implemented yet ([44]/[45])",
                    teamId, snapshot.totalSkillPointsInvested()
            );
        }

        int stoneHeight = height - OPEN_AIR_LAYER;
        int oreBudget = (int) (16 * 16 * stoneHeight * MAX_ORE_FRACTION); // 40% stone floor (§3.1)
        ChunkPos chunkPos = chunk.getPos();
        RandomSource random = RandomSource.create(seedFor(chunkPos));
        BlockState coalOre = Blocks.COAL_ORE.defaultBlockState();

        int placed = 0;
        for (int vein = 0; vein < PLACEHOLDER_VEINS && placed < oreBudget; vein++) {
            int cx = random.nextInt(16);
            int cy = minY + random.nextInt(stoneHeight);
            int cz = random.nextInt(16);
            for (int i = 0; i < PLACEHOLDER_VEIN_SIZE && placed < oreBudget; i++) {
                int x = cx + random.nextInt(5) - 2;
                int y = cy + random.nextInt(5) - 2;
                int z = cz + random.nextInt(5) - 2;
                if (x < 0 || x > 15 || z < 0 || z > 15 || y < minY || y >= minY + stoneHeight) {
                    continue;
                }
                BlockPos localPos = new BlockPos(x, y, z);
                if (chunk.getBlockState(localPos).is(Blocks.STONE)) {
                    chunk.setBlockState(localPos, coalOre);
                    placed++;
                }
            }
        }
    }

    /** Deterministic per-chunk seed (independent of the world seed). */
    private long seedFor(ChunkPos pos) {
        long seed = teamId.hashCode();
        seed = seed * 31 + pos.x();
        seed = seed * 31 + pos.z();
        return seed;
    }
}
