package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla 26.1.2's eight ores as plain numbers, from {@code docs/research/vanilla-ore-generation.md} section 2: each
 * feature's range, height shape and expected blocks a chunk (already net of draws outside Y-64..319), the features ADR
 * 0009 step 3 uses (no {@code ore_gold_extra}, no {@code ore_copper_large}), and emerald's mountain biome share of about
 * 0.18. The golden fixture for the identity pick here and for the classifier's rule in E4-S2, which also reads each
 * feature's id, blocks a vein, its targets and the features step 3 leaves out.
 */
public final class VanillaOres {
    public static final int FLOOR_Y = -64;
    public static final int LAYERS = 384;

    /** A feature's height shape: uniform, or vanilla's trapezoid with no plateau (a triangle). */
    enum Shape { UNIFORM, TRIANGLE }

    /**
     * @param ore the stone-band block, the group id
     * @param blocksPerChunk expected blocks a chunk, net of origins outside Y-64..319
     * @param id the placed feature
     * @param blocksPerVein research section 2's mean blocks for the feature's configured size
     */
    record Feature(String ore, Shape shape, int lowY, int highY, double blocksPerChunk, String id, double blocksPerVein) {
        double veinsPerChunk() {
            return blocksPerChunk / blocksPerVein;
        }

        /** Origin mass per overworld layer, the shape {@link #candidates()} builds. */
        double[] originMass() {
            double[] shape = new double[LAYERS];
            for (int y = Math.max(lowY, FLOOR_Y); y <= Math.min(highY, FLOOR_Y + LAYERS - 1); y++) {
                shape[y - FLOOR_Y] = this.shape == Shape.UNIFORM ? 1 : Math.min(y - lowY + 1, highY - y + 1);
            }
            return shape;
        }
    }

    static final List<Feature> FEATURES = List.of(
            new Feature("minecraft:coal_ore", Shape.UNIFORM, 136, 320, 664.6, "minecraft:ore_coal_upper", 22.15),
            new Feature("minecraft:coal_ore", Shape.TRIANGLE, 0, 192, 443.0, "minecraft:ore_coal_lower", 22.15),
            new Feature("minecraft:iron_ore", Shape.TRIANGLE, 80, 384, 558.0, "minecraft:ore_iron_upper", 6.79),
            new Feature("minecraft:iron_ore", Shape.TRIANGLE, -24, 56, 67.9, "minecraft:ore_iron_middle", 6.79),
            new Feature("minecraft:iron_ore", Shape.UNIFORM, -64, 72, 19.7, "minecraft:ore_iron_small", 1.99),
            new Feature("minecraft:copper_ore", Shape.TRIANGLE, -16, 112, 134.3, "minecraft:ore_copper", 8.39),
            new Feature("minecraft:gold_ore", Shape.TRIANGLE, -64, 32, 27.2, "minecraft:ore_gold", 6.79),
            new Feature("minecraft:gold_ore", Shape.UNIFORM, -64, -48, 3.1, "minecraft:ore_gold_lower", 6.79),
            new Feature("minecraft:redstone_ore", Shape.UNIFORM, -64, 15, 22.4, "minecraft:ore_redstone", 5.70),
            new Feature("minecraft:redstone_ore", Shape.TRIANGLE, -96, -32, 21.4, "minecraft:ore_redstone_lower", 5.70),
            new Feature("minecraft:lapis_ore", Shape.TRIANGLE, -32, 32, 9.9, "minecraft:ore_lapis", 4.97),
            new Feature("minecraft:lapis_ore", Shape.UNIFORM, -64, 64, 19.6, "minecraft:ore_lapis_buried", 4.97),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 6.8, "minecraft:ore_diamond", 1.99),
            new Feature("minecraft:diamond_ore", Shape.UNIFORM, -64, -4, 11.1, "minecraft:ore_diamond_medium", 5.70),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 0.6, "minecraft:ore_diamond_large", 11.59),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 11.1, "minecraft:ore_diamond_buried", 5.70),
            new Feature("minecraft:emerald_ore", Shape.TRIANGLE, -16, 480, 36.1, "minecraft:ore_emerald", 0.46));

    /**
     * Research section 2's features that step 3 leaves out or that are not ores, with their biome shares on vanilla's
     * 54-biome overworld: badlands' three biomes for extra gold, dripstone caves for large copper, lush caves for clay.
     * Only the classifier's input carries them; {@link #candidates()} is the picker's fixture and never did.
     */
    static final List<Feature> NOT_USED = List.of(
            new Feature("minecraft:gold_ore", Shape.UNIFORM, 32, 256, 340.0, "minecraft:ore_gold_extra", 6.79),
            new Feature("minecraft:copper_ore", Shape.TRIANGLE, -16, 112, 527.0, "minecraft:ore_copper_large", 32.91),
            new Feature("minecraft:gravel", Shape.UNIFORM, -64, 319, 1565.0, "minecraft:ore_gravel", 112.2),
            new Feature("minecraft:clay", Shape.UNIFORM, -64, 256, 5138.0, "minecraft:ore_clay", 112.2));

    /** Each feature's share of the overworld's biomes that list it; 1.0 where it is not named here. */
    static final Map<String, Double> FEATURE_BIOME_SHARE = Map.of(
            "minecraft:ore_emerald", 0.18,
            "minecraft:ore_copper", 53.0 / 54,
            "minecraft:ore_gold_extra", 3.0 / 54,
            "minecraft:ore_copper_large", 1.0 / 54,
            "minecraft:ore_clay", 1.0 / 54);

    /** Research section 2's expected blocks a chunk per ore, summed over the features step 3 uses. */
    static final Map<String, Double> EXPECTED_BLOCKS = Map.of(
            "minecraft:coal_ore", 1107.6, "minecraft:iron_ore", 645.6, "minecraft:copper_ore", 134.3,
            "minecraft:gold_ore", 30.3, "minecraft:redstone_ore", 43.8, "minecraft:lapis_ore", 29.5,
            "minecraft:diamond_ore", 29.6, "minecraft:emerald_ore", 36.1);

    /** {@code #c:ores} on vanilla: both variants of the eight, and the nether's three, which no overworld feature places. */
    static final Set<String> ORE_TAG = Set.of(
            "minecraft:coal_ore", "minecraft:deepslate_coal_ore", "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore", "minecraft:deepslate_copper_ore", "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore", "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore", "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:nether_gold_ore",
            "minecraft:nether_quartz_ore", "minecraft:ancient_debris");

    static final Map<String, Rarity> RARITY = Map.of(
            "minecraft:coal_ore", Rarity.COMMON, "minecraft:iron_ore", Rarity.COMMON, "minecraft:copper_ore", Rarity.COMMON,
            "minecraft:gold_ore", Rarity.UNCOMMON, "minecraft:redstone_ore", Rarity.UNCOMMON, "minecraft:lapis_ore", Rarity.UNCOMMON,
            "minecraft:diamond_ore", Rarity.RARE, "minecraft:emerald_ore", Rarity.RARE);

    /** Emerald's one feature is listed by the mountain biomes only (ADR 0009, BAL-2). */
    static final Map<String, Double> BIOME_SHARE = Map.of("minecraft:emerald_ore", 0.18);

    private VanillaOres() {
    }

    /** {@link #ORE_TAG}, for tests outside this package. */
    public static Set<String> oreTag() {
        return ORE_TAG;
    }

    public static Rarity rarityOf(String ore) {
        return RARITY.get(ore);
    }

    /** Every vanilla ore as a pick candidate: weight and per-layer profile. */
    public static List<OrePicker.Candidate> candidates() {
        Map<String, double[]> masses = new LinkedHashMap<>();
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Feature feature : FEATURES) {
            double[] shape = new double[LAYERS];
            double total = 0;
            for (int y = Math.max(feature.lowY(), FLOOR_Y); y <= Math.min(feature.highY(), FLOOR_Y + LAYERS - 1); y++) {
                double w = feature.shape() == Shape.UNIFORM ? 1 : Math.min(y - feature.lowY() + 1, feature.highY() - y + 1);
                shape[y - FLOOR_Y] = w;
                total += w;
            }
            double[] ore = masses.computeIfAbsent(feature.ore(), k -> new double[LAYERS]);
            for (int i = 0; i < LAYERS; i++) {
                ore[i] += feature.blocksPerChunk() * shape[i] / total;
            }
            weights.merge(feature.ore(), feature.blocksPerChunk() * BIOME_SHARE.getOrDefault(feature.ore(), 1.0), Double::sum);
        }
        List<OrePicker.Candidate> candidates = new ArrayList<>();
        masses.forEach((ore, mass) -> candidates.add(
                new OrePicker.Candidate(ore, RARITY.get(ore), weights.get(ore), new HeightProfile(FLOOR_Y, mass))));
        return candidates;
    }

    /**
     * The classifier's input for vanilla: every feature of research section 2 as the adapter would sample it, each
     * targeting its ore's stone block in the stone band and the deepslate variant in deepslate.
     */
    public static List<FeatureSample> samples() {
        List<FeatureSample> samples = new ArrayList<>();
        for (List<Feature> list : List.of(FEATURES, NOT_USED)) {
            for (Feature feature : list) {
                samples.add(sample(feature));
            }
        }
        return samples;
    }

    static FeatureSample sample(Feature feature) {
        List<FeatureSample.Target> targets = new ArrayList<>();
        targets.add(new FeatureSample.Target(feature.ore(), true, false));
        if (ORE_TAG.contains(feature.ore())) {
            targets.add(new FeatureSample.Target(feature.ore().replace("minecraft:", "minecraft:deepslate_"), false, true));
        }
        return new FeatureSample(feature.id(), targets, feature.veinsPerChunk(), feature.blocksPerVein(),
                FEATURE_BIOME_SHARE.getOrDefault(feature.id(), 1.0), FLOOR_Y, feature.originMass());
    }

    /** The same without any rare ore, for #234's "density stays right in a pack with no rare ore". */
    public static List<OrePicker.Candidate> withoutRare() {
        return candidates().stream().filter(c -> c.rarity() != Rarity.RARE).toList();
    }
}
