package com.orevault.orevault.ore;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.orevault.orevault.core.gen.VeinShape;
import com.orevault.orevault.core.ore.FeatureSample;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.core.ore.Overrides;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.SamplingSeed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.IntProviders;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ore classifier's adapter (§4.2, §11 Ore Rarity Classification, ADR 0009 "The classifier"): it reads the overworld's
 * placed features into plain {@link FeatureSample}s, and {@link OreTable} does the rest. It runs on the main thread at
 * every server start, before any Vault is restored (ADR 0002 orders the two), and publishes one immutable
 * {@link ClassifiedOres} through a {@code volatile} field that nothing writes again in the session; {@code /reload} does
 * not reclassify. The old classifier read two placement fields through access transformers and only for uniform heights
 * with absolute anchors, so most of vanilla fell to Uncommon (research section 2, point 7). This one reads every
 * modifier through its codec and samples every height provider, so one path handles every provider type.
 *
 * <p>Determinism across restarts (Epic 4 triage, DEV-E4-5, RV-E4-6): biomes and features are iterated sorted by id, and
 * each feature is sampled from {@link SamplingSeed#of} its id, so the table and the identity pick built from it come out
 * the same every start.
 *
 * <p>A server overworld with a single biome, or one that leaves a rarity with no ore, is classified against the
 * {@code minecraft:normal} preset's overworld instead (Epic 4 triage ruling 6, amended in round 2): a skyblock or
 * superflat world would otherwise classify almost nothing and bring back the empty Vault.
 */
public final class OreClassifier {
    /** Draws per count and height provider (ADR 0009 step 6). */
    public static final int DRAWS = 4096;
    public static final String OWN_SOURCE = "the server's overworld";
    public static final String NORMAL_SOURCE = "the minecraft:normal preset's overworld";

    private static final Logger LOGGER = LoggerFactory.getLogger(OreClassifier.class);
    /** Wider than vanilla's own 256 so a modded count is read rather than refused (ADR 0009 step 5). */
    private static final int MAX_COUNT = 4096;
    /** Modifiers that change neither the count nor the height: the biome filter's work is the biome share. */
    private static final Set<String> PASS_THROUGH = Set.of("minecraft:in_square", "minecraft:biome");

    private static volatile @Nullable ClassifiedOres published;

    private OreClassifier() {
    }

    /**
     * Classifies against the server's overworld, or the normal preset's when ruling 6 says so, and logs the result: each
     * ore's rarity (#238), every override that could not apply, an empty rarity at WARN and no ore at all at ERROR.
     */
    public static ClassifiedOres classifyServer(MinecraftServer server, Overrides overrides) {
        RegistryAccess access = server.registryAccess();
        ServerLevel overworld = server.overworld();
        ChunkGenerator generator = overworld.getChunkSource().getGenerator();
        int biomes = generator.getBiomeSource().possibleBiomes().size();
        OreTable table = classify(access, generator, overworld, overrides);
        String source = OWN_SOURCE;
        if (OreTable.needsNormalPreset(table, biomes)) {
            String why = biomes <= 1
                    ? "lists " + biomes + " biome"
                    : "gives no ore of " + table.emptyRarities();
            Optional<LevelStem> normal = normalOverworld(access);
            if (normal.isPresent()) {
                DimensionType type = normal.get().type().value();
                table = classify(access, normal.get().generator(), LevelHeightAccessor.create(type.minY(), type.height()),
                        overrides);
                source = NORMAL_SOURCE;
                LOGGER.warn("The server's overworld {}, so ores are classified against {} instead", why, source);
            } else {
                LOGGER.error("The server's overworld {}, and the minecraft:normal world preset is missing; keeping {}",
                        why, source);
            }
        }
        report(table, source);
        return ClassifiedOres.of(table, source);
    }

    /**
     * ADR 0009 steps 1 to 9 against one overworld: its biome source's biomes and its heights.
     *
     * @param heights the overworld's limits; the generation context is the narrower of these and the generator's
     */
    public static OreTable classify(RegistryAccess access, ChunkGenerator generator, LevelHeightAccessor heights,
            Overrides overrides) {
        WorldGenerationContext context = new WorldGenerationContext(generator, heights);
        int floorY = context.getMinGenY();
        int layers = context.getGenDepth();
        Set<String> oreTag = oreTag(access);

        List<Holder<Biome>> biomes = new ArrayList<>(generator.getBiomeSource().possibleBiomes());
        biomes.sort(Comparator.comparing(OreClassifier::idOrEmpty));
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        Map<String, PlacedFeature> features = new TreeMap<>();
        Map<String, Integer> listedBy = new TreeMap<>();
        Set<String> warned = new HashSet<>();
        for (Holder<Biome> biome : biomes) {
            List<HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
            if (steps.size() <= step) {
                continue;
            }
            Set<String> inBiome = new HashSet<>();
            for (Holder<PlacedFeature> feature : steps.get(step)) {
                Optional<String> id = id(feature);
                if (id.isEmpty()) {
                    warnOnce(warned, "unnamed", "An unnamed placed feature in {} is passed over: it has no id to seed from",
                            idOrEmpty(biome));
                } else if (inBiome.add(id.get())) {
                    features.putIfAbsent(id.get(), feature.value());
                    listedBy.merge(id.get(), 1, Integer::sum);
                }
            }
        }

        // Registry ops, not bare JsonOps: a modifier holding a holder set does not encode without them (DEV-20).
        List<FeatureSample> samples = sampleAll(RegistryOps.create(JsonOps.INSTANCE, access), context, oreTag, features,
                listedBy, biomes.size(), warned);
        return OreTable.build(samples, oreTag, overrides, floorY, layers);
    }

    /** Generic in the ops' element type, so this package names no JSON class (ADR 0001's {@code ore} row). */
    private static <T> List<FeatureSample> sampleAll(DynamicOps<T> ops, WorldGenerationContext context, Set<String> oreTag,
            Map<String, PlacedFeature> features, Map<String, Integer> listedBy, int biomes, Set<String> warned) {
        List<FeatureSample> samples = new ArrayList<>();
        for (Map.Entry<String, PlacedFeature> feature : features.entrySet()) {
            double share = listedBy.get(feature.getKey()) / (double) biomes;
            sample(ops, context, oreTag, feature.getKey(), feature.getValue(), share, warned).ifPresent(samples::add);
        }
        return samples;
    }

    /** §10's strings parsed against the server's {@code #c:ores}, each rejected entry logged at WARN. */
    public static Overrides readOverrides(RegistryAccess access, List<String> entries) {
        Overrides overrides = Overrides.parse(entries, oreTag(access));
        overrides.rejected().forEach(reason -> LOGGER.warn("{}; it is ignored", reason));
        return overrides;
    }

    /** The one write, before Vault restoration (ADR 0009). */
    public static void publish(ClassifiedOres ores) {
        published = ores;
    }

    /** What the generator reads, or {@code null} before the first start or after a stop. */
    public static @Nullable ClassifiedOres published() {
        return published;
    }

    /** At {@code ServerStoppedEvent}: a second world in one client classifies its own pack. */
    public static void clear() {
        published = null;
    }

    /** The {@code minecraft:normal} preset's overworld stem, from the server's own registries so biome modifiers apply. */
    public static Optional<LevelStem> normalOverworld(RegistryAccess access) {
        try {
            return access.lookupOrThrow(Registries.WORLD_PRESET).get(WorldPresets.NORMAL)
                    .flatMap(preset -> preset.value().overworld());
        } catch (IllegalStateException e) {
            LOGGER.error("No world preset registry to find minecraft:normal in", e);
            return Optional.empty();
        }
    }

    /** Steps 2 to 7 for one placed feature: nothing when it is not an ore feature or its numbers cannot be read. */
    private static <T> Optional<FeatureSample> sample(DynamicOps<T> ops, WorldGenerationContext context, Set<String> oreTag,
            String id, PlacedFeature placed, double biomeShare, Set<String> warned) {
        ConfiguredFeature<?, ?> configured = placed.feature().value();
        if (!(configured.config() instanceof OreConfiguration ore)) {
            return Optional.empty();
        }
        RandomSource ruleRandom = RandomSource.create(0L);
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        List<FeatureSample.Target> targets = new ArrayList<>();
        for (OreConfiguration.TargetBlockState target : ore.targetStates) {
            String block = BuiltInRegistries.BLOCK.getKey(target.state.getBlock()).toString();
            targets.add(new FeatureSample.Target(block, target.target.test(stone, ruleRandom),
                    target.target.test(deepslate, ruleRandom)));
        }
        if (targets.stream().noneMatch(target -> oreTag.contains(target.blockId()))) {
            return Optional.empty();
        }

        double blocksPerVein;
        if (configured.feature() == Feature.SCATTERED_ORE) {
            // ScatteredOreFeature tries random.nextInt(size + 1) placements, each in solid rock (step 7).
            blocksPerVein = ore.size / 2.0;
        } else if (ore.size > VeinShape.MAX_TABLED_SIZE) {
            LOGGER.warn("{} has vein size {}, past the {} the vein shape is tabled for; passed over, so its ore needs an "
                    + "override to be placed", id, ore.size, VeinShape.MAX_TABLED_SIZE);
            return Optional.empty();
        } else {
            if (configured.feature() != Feature.ORE) {
                warnOnce(warned, "feature:" + configured.feature(), "{} uses an OreConfiguration with a feature that is "
                        + "neither ore nor scattered_ore; its veins are counted as minecraft:ore's", id);
            }
            blocksPerVein = VeinShape.meanBlocks(ore.size);
        }

        RandomSource random = RandomSource.create(SamplingSeed.of(id));
        double veins = 1;
        @Nullable HeightProvider height = null;
        for (PlacementModifier modifier : placed.placement()) {
            DataResult<T> encodedResult = PlacementModifier.CODEC.encodeStart(ops, modifier);
            Optional<T> encoded = encodedResult.result();
            if (encoded.isEmpty()) {
                LOGGER.warn("{}: a placement modifier does not encode ({}); the feature is passed over", id,
                        encodedResult.error().map(DataResult.Error::message).orElse("no message"));
                return Optional.empty();
            }
            String type = ops.get(encoded.get(), "type").flatMap(ops::getStringValue).result().orElse("?");
            switch (type) {
                case "minecraft:count" -> {
                    Optional<IntProvider> count = field(ops, encoded.get(), "count", IntProviders.codec(0, MAX_COUNT));
                    if (count.isEmpty()) {
                        LOGGER.warn("{}: its count does not decode; the feature is passed over", id);
                        return Optional.empty();
                    }
                    veins *= mean(count.get(), random);
                }
                case "minecraft:rarity_filter" -> {
                    Optional<Integer> chance = field(ops, encoded.get(), "chance", Codec.INT);
                    if (chance.isEmpty() || chance.get() <= 0) {
                        LOGGER.warn("{}: its rarity filter does not decode; the feature is passed over", id);
                        return Optional.empty();
                    }
                    // RarityFilter#shouldPlace is random.nextFloat() < 1.0F / chance.
                    veins *= 1.0 / chance.get();
                }
                case "minecraft:height_range" -> {
                    Optional<HeightProvider> provider = field(ops, encoded.get(), "height", HeightProvider.CODEC);
                    if (provider.isEmpty()) {
                        LOGGER.warn("{}: its height does not decode; the feature is passed over", id);
                        return Optional.empty();
                    }
                    // The chain sets the height again at each height modifier, so the last one decides.
                    height = provider.get();
                }
                default -> {
                    if (!PASS_THROUGH.contains(type)) {
                        warnOnce(warned, "modifier:" + type,
                                "Placement modifier {} is not one the classifier reads; passed over (first seen on {})",
                                type, id);
                    }
                }
            }
        }
        if (height == null) {
            LOGGER.warn("{} has no height_range modifier, so the classifier cannot place its veins; passed over", id);
            return Optional.empty();
        }

        int floorY = context.getMinGenY();
        int layers = context.getGenDepth();
        double[] origins = new double[layers];
        int kept = 0;
        for (int draw = 0; draw < DRAWS; draw++) {
            int y = height.sample(random, context);
            if (y >= floorY && y < floorY + layers) {
                origins[y - floorY]++;
                kept++;
            }
        }
        // Draws outside the overworld are dropped and lower the expected count, as diamond's range below the floor does.
        veins *= kept / (double) DRAWS;
        return Optional.of(new FeatureSample(id, targets, veins, blocksPerVein, biomeShare, floorY, origins));
    }

    private static <T, R> Optional<R> field(DynamicOps<T> ops, T encoded, String key, Codec<R> codec) {
        return ops.get(encoded, key).flatMap(value -> codec.parse(ops, value)).result();
    }

    private static double mean(IntProvider provider, RandomSource random) {
        long sum = 0;
        for (int draw = 0; draw < DRAWS; draw++) {
            sum += provider.sample(random);
        }
        return sum / (double) DRAWS;
    }

    private static void report(OreTable table, String source) {
        table.notes().forEach(LOGGER::warn);
        for (OreTable.Entry entry : table.entries()) {
            String override = entry.classifiedAs() == null
                    ? ", named only by an override (flat profile)"
                    : entry.classifiedAs() != entry.rarity() ? ", overridden from " + entry.classifiedAs() : "";
            LOGGER.info("Ore {}: {}, {} blocks a chunk, {} a vein, weight {}, from {}{}", entry.groupId(), entry.rarity(),
                    String.format("%.1f", entry.expectedBlocks()), String.format("%.2f", entry.meanBlocksPerVein()),
                    String.format("%.1f", entry.weight()), entry.features(), override);
        }
        if (!table.unclassified().isEmpty()) {
            LOGGER.info("In #c:ores but not classified, so never placed and paying nothing: {}", table.unclassified());
        }
        if (table.isEmpty()) {
            LOGGER.error("No ore was classified against {}: Vaults would generate with no ore", source);
            return;
        }
        for (Rarity rarity : table.emptyRarities()) {
            LOGGER.warn("No {} ore in this pack: those veins take their ore from the nearest rarity that has some, so "
                    + "density holds and Resonance per ore is lower", rarity);
        }
        LOGGER.info("Classified {} ores against {}", table.entries().size(), source);
    }

    private static void warnOnce(Set<String> warned, String key, String message, Object... arguments) {
        if (warned.add(key)) {
            LOGGER.warn(message, arguments);
        }
    }

    private static Set<String> oreTag(RegistryAccess access) {
        Set<String> ids = new TreeSet<>();
        access.lookupOrThrow(Registries.BLOCK).get(Tags.Blocks.ORES)
                .ifPresent(set -> set.forEach(block -> id(block).ifPresent(ids::add)));
        return ids;
    }

    private static Optional<String> id(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.identifier().toString());
    }

    private static String idOrEmpty(Holder<?> holder) {
        return id(holder).orElse("");
    }
}
