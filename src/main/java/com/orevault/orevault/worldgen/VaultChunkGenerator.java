package com.orevault.orevault.worldgen;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.ore.OreClassifier.Rarity;
import com.orevault.orevault.ore.VaultOreTable;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * Custom chunk generator for team Vault dimensions (§3.1, §11): an open air
 * layer at the top, then an overworld-style solid fill. The exact layering is
 * data-driven through {@link VaultLayerConfig} (#76) — loaded per dimension
 * type from {@code data/orevault/worldgen/vault_layers/<type>.json}, with the
 * classic stack (bedrock, deepslate to Y=0, stone, dirt band, grass surface,
 * air layer) as the built-in fallback. No aquifers, caves, or structures. Ore
 * placement is driven by the team's skill state.
 *
 * <p>The generator is created per team by {@code VaultDimensions} and reads a
 * main-thread-maintained {@link SkillSnapshot} at generation time so node
 * purchases affect newly generated chunks without a restart (§11). Node-driven
 * placement math is a documented hook — #45 and #46 implement it on top of the
 * baseline distribution in {@link OreDistribution}.</p>
 *
 * <p>The 40% stone floor (§3.1) is enforced by capping the ore budget at 60%
 * of the chunk's stone volume; no skill state can ever push stone below 40%.</p>
 */
public final class VaultChunkGenerator extends ChunkGenerator {

    /** Hard 40% stone floor: ores may never exceed 60% of a chunk's stone volume (§3.1). */
    public static final double MAX_ORE_FRACTION = 0.60;
    /**
     * Offsets rolled per block a vein wants, before it gives up on that vein.
     *
     * <p>Offsets land in a 5x5x5 cube around the vein's origin, so most rolls succeed and the
     * multiplier only covers collisions and the edges of the chunk. It exists so a vein whose
     * origin sits against a chunk wall costs a bounded number of rolls rather than looping.</p>
     */
    private static final int VEIN_ATTEMPTS_PER_BLOCK = 4;

    private final UUID teamId;
    private final int minY;
    private final int height;
    private final VaultLayerConfig layers;
    private final Supplier<SkillSnapshot> skills;
    private final AtomicBoolean loggedSkillState = new AtomicBoolean();

    public VaultChunkGenerator(UUID teamId, Holder<Biome> biome, int minY, int height, Supplier<SkillSnapshot> skills, VaultLayerConfig layers) {
        super(new FixedBiomeSource(biome));
        this.teamId = teamId;
        this.minY = minY;
        this.height = height;
        this.skills = skills;
        this.layers = layers;
    }

    /** Team id this generator was created for. */
    public UUID teamId() {
        return teamId;
    }

    /**
     * Immutable, thread-safe snapshot of the team's skill state, maintained on
     * the main thread by {@code VaultDimensions} and read at chunk generation
     * time (which runs on a background executor).
     *
     * <p>Tiers, not a set of ids. Nearly every §6.1 node has more than one tier and its effect
     * differs at each, so a boolean "is it bought" answers the wrong question — the one caller that
     * asked it that way ({@code VaultBreakContext#hasResonanceNode}) could only ever have
     * implemented a tier-1 node correctly.</p>
     */
    public record SkillSnapshot(
            Map<String, Integer> resonanceTiers,
            Map<String, Integer> animusTiers,
            int totalSkillPointsInvested) {

        public static final SkillSnapshot EMPTY = new SkillSnapshot(Map.of(), Map.of(), 0);

        /**
         * Builds a snapshot from the team's SavedData (main thread only).
         *
         * <p>{@code Map.copyOf} rather than the tree's own map: the snapshot is read from the
         * generation executor, and handing that thread a view of a live, main-thread-mutated map is
         * the exact race the snapshot exists to prevent.</p>
         */
        public static SkillSnapshot of(OreVaultTeamData data) {
            return new SkillSnapshot(
                    Map.copyOf(data.resonanceTree().getUnlockedTiers()),
                    Map.copyOf(data.animusTree().getUnlockedTiers()),
                    data.resonanceTree().skillPointsInvested() + data.animusTree().skillPointsInvested()
            );
        }

        /** Unlocked tier of a Resonance node, 0 when it is not bought. */
        public int resonanceTier(String nodeId) {
            return resonanceTiers.getOrDefault(nodeId, 0);
        }

        /** Unlocked tier of an Animus node, 0 when it is not bought. */
        public int animusTier(String nodeId) {
            return animusTiers.getOrDefault(nodeId, 0);
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
        // First free space: the bottom of the air layer (one block above the surface).
        int firstAirY = layers.firstAirY();
        return firstAirY >= 0 ? firstAirY : minY + height;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        BlockState[] states = new BlockState[height];
        for (int y = 0; y < height; y++) {
            states[y] = layers.blockAt(minY + y);
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
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int y = minY; y <= maxY; y++) {
            BlockState state = layers.blockAt(y);
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
     * Places the chunk's ore (§3.1).
     *
     * <p>Every number is in {@link OreDistribution}, where it can be tested; every ore comes from
     * {@link OreClassifier}'s server-start scan of {@code #c:ores}, so a pack's modded ore generates
     * with no per-mod work here and the thing that decides what an ore is worth is the thing that
     * decides how often it appears. This file owns placement and nothing else.</p>
     *
     * <p>Node modifiers are still a hook: #45 and #46 add vein-count, size and rarity changes on top
     * of this baseline. The 40% stone floor is enforced inside {@code oreBudget} rather than here,
     * so no future node can raise a density past it.</p>
     *
     * <p>Reads the {@link SkillSnapshot} and {@link OreClassifier}'s published table, both immutable
     * and both safe from the generation executor. Nothing here touches {@code OreVaultTeamData}.</p>
     */
    private void placeOres(ChunkAccess chunk) {
        SkillSnapshot snapshot = skills.get();
        if (loggedSkillState.compareAndSet(false, true) && !snapshot.isEmpty()) {
            OreVault.LOGGER.debug(
                    "Vault chunk gen: team {} has {} skill points invested; node modifiers not implemented yet (#45/#46)",
                    teamId, snapshot.totalSkillPointsInvested()
            );
        }

        VaultOreTable ores = OreClassifier.oreTable();
        List<VaultLayerConfig.OreBand> bands = layers.oreBands();
        if (ores.isEmpty() || bands.isEmpty()) {
            // No classified ore (before the first scan) or no mineralizable layer. Generating
            // nothing is the right answer to both; guessing at an ore set is not.
            return;
        }

        // Depth preferences are expressed against the whole mineralizable column, so an ore sits at
        // the same relative height whether or not this dimension type has a deepslate band.
        int columnBottom = bands.get(0).bottom();
        int columnTop = bands.get(bands.size() - 1).top();
        RandomSource random = RandomSource.create(seedFor(chunk.getPos()));

        for (VaultLayerConfig.OreBand band : bands) {
            placeBand(chunk, band, ores, random, columnBottom, columnTop);
        }
    }

    /** Fills one band up to its ore budget, a vein at a time. */
    private void placeBand(
            ChunkAccess chunk, VaultLayerConfig.OreBand band, VaultOreTable ores,
            RandomSource random, int columnBottom, int columnTop
    ) {
        int budget = OreDistribution.oreBudget(band.volumePerChunk(), band.density());
        int placed = 0;
        // One vein can place nothing at all if every offset it rolls lands outside the chunk, so the
        // loop is bounded by attempts rather than by progress. A band whose budget is unreachable
        // costs a bounded number of rolls instead of spinning.
        for (int attempt = 0; placed < budget && attempt < budget; attempt++) {
            Rarity rarity = OreDistribution.rollRarity(random.nextDouble());
            List<VaultOreTable.Entry> candidates = ores.ofOrFallback(rarity);
            if (candidates.isEmpty()) {
                return;
            }
            VaultOreTable.Entry ore = candidates.get(random.nextInt(candidates.size()));
            int size = Math.min(
                    OreDistribution.veinSize(rarity, random.nextDouble()), budget - placed);
            int preferred = OreDistribution.preferredY(ore.normalizedDepth(), columnBottom, columnTop);
            int centreY = OreDistribution.clampToBand(preferred, band.bottom(), band.top());
            placed += placeVein(chunk, ore.state(), band, random, centreY, size);
        }
    }

    /**
     * Places one vein of up to {@code size} blocks around a random column of the chunk.
     *
     * <p>Only replaces the band's own filler, so a vein never overwrites one already placed and
     * never leaks into the dirt or the bedrock if a height lands on a boundary.</p>
     *
     * @return how many blocks it actually placed
     */
    private int placeVein(
            ChunkAccess chunk, BlockState ore, VaultLayerConfig.OreBand band,
            RandomSource random, int centreY, int size
    ) {
        int centreX = random.nextInt(16);
        int centreZ = random.nextInt(16);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int i = 0; i < size * VEIN_ATTEMPTS_PER_BLOCK && placed < size; i++) {
            int x = centreX + random.nextInt(5) - 2;
            int y = centreY + random.nextInt(5) - 2;
            int z = centreZ + random.nextInt(5) - 2;
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < band.bottom() || y >= band.top()) {
                continue;
            }
            pos.set(x, y, z);
            if (chunk.getBlockState(pos).is(band.filler().getBlock())) {
                chunk.setBlockState(pos, ore);
                placed++;
            }
        }
        return placed;
    }

    /** Deterministic per-chunk seed (independent of the world seed). */
    private long seedFor(ChunkPos pos) {
        return OreDistribution.chunkSeed(teamId, pos.x(), pos.z());
    }
}
