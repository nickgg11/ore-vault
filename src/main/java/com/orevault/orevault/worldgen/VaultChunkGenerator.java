package com.orevault.orevault.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.skill.NodeCosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Custom ChunkGenerator for team Vault dimensions (design spec section 11). Reads the
 * team's skill tree state from SavedData at chunk generation time, so node purchases affect
 * newly generated chunks immediately. Enforces the 40% stone content floor.
 */
public class VaultChunkGenerator extends ChunkGenerator {
    public static final MapCodec<VaultChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("team_id").forGetter(g -> g.teamId.toString()),
            Codec.BOOL.optionalFieldOf("expanded", false).forGetter(g -> g.expanded)
    ).apply(instance, (teamId, expanded) -> new VaultChunkGenerator(UUID.fromString(teamId), expanded)));

    private final UUID teamId;
    private final boolean expanded;
    private long legacySeed;

    public VaultChunkGenerator(UUID teamId, boolean expanded) {
        super(new FixedBiomeSource(plainBiome()));
        this.teamId = teamId;
        this.expanded = expanded;
    }

    private static Holder<Biome> plainBiome() {
        return net.minecraft.core.registries.BuiltInRegistries.BIOME
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
    }

    public UUID teamId() {
        return teamId;
    }

    public boolean isExpanded() {
        return expanded;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(net.minecraft.core.HolderLookup<StructureSet> structureSets,
                                                    RandomState randomState, long legacyLevelSeed) {
        this.legacySeed = legacyLevelSeed;
        return super.createState(structureSets, randomState, legacyLevelSeed);
    }

    // --- geometry --------------------------------------------------------------

    public int vaultMinY() {
        return expanded ? -64 : 0;
    }

    public int vaultHeight() {
        return expanded ? 384 : 256;
    }

    public int vaultTopY() {
        return vaultMinY() + vaultHeight() - 1;
    }

    @Override
    public int getGenDepth() {
        return vaultHeight();
    }

    @Override
    public int getSeaLevel() {
        return vaultMinY();
    }

    @Override
    public int getMinY() {
        return vaultMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return vaultTopY();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        int minY = vaultMinY();
        int height = vaultHeight();
        BlockState[] states = new BlockState[height];
        for (int i = 0; i < height; i++) {
            states[i] = surfaceState(minY + i);
        }
        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos) {
        result.add("Ore Vault (" + teamId + (expanded ? ", expanded" : "") + ")");
    }

    private BlockState surfaceState(int y) {
        // Surface: deepslate for the top layers; stone below. Expanded vaults are deepslate
        // below Y=0 (the ultra-deep layer).
        if (expanded && y < 0) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        if (y >= vaultTopY() - 2) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        return Blocks.STONE.defaultBlockState();
    }

    // --- generation --------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structureManager, ChunkAccess chunk) {
        OreVaultTeamData data = null;
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            data = OreVaultTeamData.get(server, teamId);
        }
        GenConfig config = GenConfig.from(data);
        ChunkPos chunkPos = chunk.getPos();

        long seed = legacySeed ^ (long) teamId.hashCode() * 31L + chunkPos.x * 341873128712L + chunkPos.z * 132897987541L;
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(seed));

        int minY = vaultMinY();
        int topY = vaultTopY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. Base fill
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= topY; y++) {
                    pos.set(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                    chunk.setBlockState(pos, surfaceState(y), false);
                }
            }
        }
        if (data != null) {
            data.addChunksGenerated(1);
            data.addBlocksGenerated((long) 16 * 16 * vaultHeight());
        }

        // 2. Gravel / clay patches (unless Gravel Purge)
        if (!config.gravelPurge) {
            int patches = 3;
            for (int i = 0; i < patches; i++) {
                placeBlob(chunk, random, Blocks.GRAVEL.defaultBlockState(), 18, minY, topY, chunkPos, 1.0);
                placeBlob(chunk, random, Blocks.CLAY.defaultBlockState(), 12, minY, topY, chunkPos, 1.0);
            }
        }

        // 3. Ore veins with a hard stone floor: never more than MAX_ORE_FRACTION ore by volume.
        long maxOreBlocks = (long) (16 * 16 * (long) vaultHeight() * NodeCosts.MAX_ORE_FRACTION);
        long placed = 0;
        placed += placeOreClass(chunk, random, chunkPos, config, OreClassifier.OreClass.RARE, maxOreBlocks - placed, minY, topY);
        placed += placeOreClass(chunk, random, chunkPos, config, OreClassifier.OreClass.UNCOMMON, maxOreBlocks - placed, minY, topY);
        placed += placeOreClass(chunk, random, chunkPos, config, OreClassifier.OreClass.COMMON, maxOreBlocks - placed, minY, topY);

        // 4. Ancient debris (Ancient Traces node)
        if (config.ancientDebrisVeins > 0) {
            for (int i = 0; i < config.ancientDebrisVeins; i++) {
                placeBlob(chunk, random, Blocks.ANCIENT_DEBRIS.defaultBlockState(), 2 + random.nextInt(2),
                        minY, Math.min(topY, Math.max(minY, 30)), chunkPos, 1.0);
            }
        }

        // 5. Amethyst geodes (Geode Clusters node)
        if (config.geodeChance > 0 && random.nextDouble() < config.geodeChance) {
            placeGeode(chunk, random, chunkPos, minY, topY);
        }

        return CompletableFuture.completedFuture(chunk);
    }

    private long placeOreClass(ChunkAccess chunk, WorldgenRandom random, ChunkPos chunkPos, GenConfig config,
                               OreClassifier.OreClass oreClass, long budget, int minY, int topY) {
        List<Block> ores = OreClassifier.oresOf(oreClass);
        if (ores.isEmpty() || budget <= 0) {
            return 0;
        }
        long placed = 0;
        int veins = (int) Math.round(config.veinCountFor(oreClass));
        for (int v = 0; v < veins && placed < budget; v++) {
            Block ore = ores.get(random.nextInt(ores.size()));
            Block deepslate = OreClassifier.deepslateVariant(ore);
            BlockState state = ore.defaultBlockState();
            int size = (int) Math.round(config.veinSizeFor(oreClass) * (0.75 + random.nextDouble() * 0.5));
            int y = pickVeinY(random, config, minY, topY);
            if (random.nextInt(100) < config.ultraDeepBonusChance(y, minY)) {
                size = size * 2;
            }
            placed += placeBlob(chunk, random, state, size, y - 4, y + 4, chunkPos, 1.0);
            if (deepslate != null) {
                // small chance to also drop a deepslate variant nearby
                placed += placeBlob(chunk, random, deepslate.defaultBlockState(), Math.max(1, size / 2), y - 6, y + 2, chunkPos, 0.4);
            }
        }
        return placed;
    }

    private int pickVeinY(WorldgenRandom random, GenConfig config, int minY, int topY) {
        // Deep Veins shifts weighting toward lower Y.
        int span = topY - minY;
        int y;
        if (config.deepVeinShift <= 0) {
            y = minY + random.nextInt(span);
        } else {
            // weight lower half more heavily
            int half = minY + span / 2;
            if (random.nextInt(100) < 35 + config.deepVeinShift * 15) {
                y = minY + random.nextInt(Math.max(1, half - minY));
            } else {
                y = half + random.nextInt(Math.max(1, topY - half));
            }
        }
        return y;
    }

    /** Random-walk blob placement; returns the number of blocks placed. */
    private long placeBlob(ChunkAccess chunk, WorldgenRandom random, BlockState state, int size,
                           int minY, int maxY, ChunkPos chunkPos, double density) {
        long placed = 0;
        int cx = chunkPos.getMinBlockX() + random.nextInt(16);
        int cy = clamp(random.nextInt(Math.max(1, maxY - minY)) + minY, minY, maxY);
        int cz = chunkPos.getMinBlockZ() + random.nextInt(16);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(cx, cy, cz);
        for (int i = 0; i < size; i++) {
            if (density < 1.0 && random.nextDouble() > density) {
                pos.setWithOffset(pos, random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
                continue;
            }
            if (pos.getY() >= minY && pos.getY() <= maxY) {
                BlockState current = chunk.getBlockState(pos);
                if (current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE) || current.is(Blocks.TUFF)) {
                    chunk.setBlockState(pos, state, false);
                    placed++;
                }
            }
            pos.setWithOffset(pos, random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        }
        return placed;
    }

    private void placeGeode(ChunkAccess chunk, WorldgenRandom random, ChunkPos chunkPos, int minY, int topY) {
        int cx = chunkPos.getMinBlockX() + random.nextInt(16);
        int cy = minY + random.nextInt(Math.max(1, topY - minY));
        int cz = chunkPos.getMinBlockZ() + random.nextInt(16);
        int radius = 4 + random.nextInt(4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > radius || d < radius - 1.5) {
                        continue;
                    }
                    pos.set(cx + dx, cy + dy, cz + dz);
                    if (pos.getY() < minY || pos.getY() > topY) {
                        continue;
                    }
                    BlockState current = chunk.getBlockState(pos);
                    if (!(current.is(Blocks.STONE) || current.is(Blocks.DEEPSLATE) || current.is(Blocks.TUFF))) {
                        continue;
                    }
                    BlockState shell = d > radius - 0.75 ? Blocks.SMOOTH_BASALT.defaultBlockState()
                            : Blocks.AMETHYST_BLOCK.defaultBlockState();
                    if (random.nextInt(10) == 0) {
                        shell = Blocks.BUDDING_AMETHYST.defaultBlockState();
                    }
                    chunk.setBlockState(pos, shell, false);
                }
            }
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // --- no-op worldgen phases ---------------------------------------------------

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             net.minecraft.world.level.biome.BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk) {
        // No carvers: the Vault is solid stone by design.
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess protoChunk) {
        // Surface is baked in fillFromNoise.
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
        // No ambient mob spawning.
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        // No vanilla biome features (trees, etc.) in the Vault.
    }

    @Override
    public void createStructures(net.minecraft.core.RegistryAccess registryAccess,
                                 ChunkGeneratorStructureState state, StructureManager structureManager,
                                 ChunkAccess chunk, net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
                                 net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
        // No structures.
    }

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structureManager, ChunkAccess centerChunk) {
        // No structure references.
    }

    /**
     * Per-chunk generation configuration derived from the team's skill tree state at
     * generation time.
     */
    public record GenConfig(
            double veinSizeMultiplier, double veinCountMultiplier,
            double commonBoost, double uncommonBoost, double rareBoost,
            int deepVeinShift, boolean gravelPurge, int stoneReductionTier,
            double geodeChance, int ancientDebrisVeins,
            boolean abundance, int abundanceTier, boolean motherlode, int motherlodeTier,
            boolean volatileVeins, boolean expandedVault, boolean vaultExpansionNode
    ) {
        static final GenConfig DEFAULT = new GenConfig(1.0, 1.0, 1.0, 1.0, 1.0, 0, false, 0,
                0, 0, false, 0, false, 0, false, false, false);

        public static GenConfig from(OreVaultTeamData data) {
            if (data == null) {
                return DEFAULT;
            }
            double size = 1.0;
            int ve = data.nodeTier("vein_expansion");
            size += switch (ve) {
                case 1 -> 0.15;
                case 2 -> 0.30;
                case 3 -> 0.50;
                case 4 -> 0.75;
                case 5 -> 1.00;
                default -> 0;
            };
            double count = 1.0;
            int vp = data.nodeTier("vein_proliferation");
            count += switch (vp) {
                case 1 -> 0.20;
                case 2 -> 0.40;
                case 3 -> 0.65;
                case 4 -> 0.90;
                case 5 -> 1.20;
                default -> 0;
            };
            double common = 1 + switch (data.nodeTier("common_ore_boost")) {
                case 1 -> 0.25;
                case 2 -> 0.50;
                case 3 -> 0.80;
                default -> 0;
            };
            double uncommon = 1 + switch (data.nodeTier("uncommon_ore_boost")) {
                case 1 -> 0.25;
                case 2 -> 0.50;
                case 3 -> 0.80;
                default -> 0;
            };
            double rare = 1 + switch (data.nodeTier("rare_ore_boost")) {
                case 1 -> 0.30;
                case 2 -> 0.60;
                case 3 -> 1.00;
                default -> 0;
            };
            int deep = data.nodeTier("deep_veins");
            boolean purge = data.hasNode("gravel_purge");
            int stoneReduction = data.nodeTier("stone_reduction");
            int geodeTier = data.nodeTier("geode_clusters");
            double geodeChance = switch (geodeTier) {
                case 1 -> 0.05;
                case 2 -> 0.10;
                default -> 0;
            };
            int debris = data.nodeTier("ancient_traces");
            int abundanceTier = data.nodeTier("abundance");
            int motherlodeTier = data.nodeTier("motherlode");
            boolean volatileVeins = data.isTradeoffActiveForAnyone("volatile_veins");
            if (volatileVeins) {
                size += 0.25;
            }
            if (abundanceTier > 0) {
                count += abundanceTier == 1 ? 0.50 : 1.00;
            }
            if (motherlodeTier > 0) {
                size += motherlodeTier == 1 ? 1.00 : 1.50;
                count *= 0.5;
            }
            return new GenConfig(size, count, common, uncommon, rare, deep, purge, stoneReduction,
                    geodeChance, debris, abundanceTier > 0, abundanceTier, motherlodeTier > 0,
                    motherlodeTier, volatileVeins, false, data.vaultExpanded() && data.hasNode("vault_expansion"));
        }

        /** Base vein count per chunk for an ore class. */
        public double veinCountFor(OreClassifier.OreClass oreClass) {
            double base = switch (oreClass) {
                case COMMON -> 10;
                case UNCOMMON -> 5;
                case RARE -> 2;
            };
            double boost = switch (oreClass) {
                case COMMON -> commonBoost;
                case UNCOMMON -> uncommonBoost;
                case RARE -> rareBoost;
            };
            return base * boost * veinCountMultiplier;
        }

        /** Base vein size for an ore class. */
        public double veinSizeFor(OreClassifier.OreClass oreClass) {
            double base = switch (oreClass) {
                case COMMON -> 6;
                case UNCOMMON -> 4.5;
                case RARE -> 3;
            };
            return base * veinSizeMultiplier;
        }

        /** Extra density bonus below Y=30 when Deep Veins is purchased. */
        public int ultraDeepBonusChance(int y, int minY) {
            if (deepVeinShift > 0 && y < minY + 30) {
                return deepVeinShift == 1 ? 30 : 50;
            }
            return 0;
        }
    }
}
