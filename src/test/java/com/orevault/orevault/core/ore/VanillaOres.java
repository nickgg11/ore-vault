package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla 26.1.2's eight ores as plain numbers, from {@code docs/research/vanilla-ore-generation.md} section 2: each
 * feature's range, height shape and expected blocks a chunk (already net of draws outside Y-64..319), the features ADR
 * 0009 step 3 uses (no {@code ore_gold_extra}, no {@code ore_copper_large}), and emerald's mountain biome share of about
 * 0.18. The golden fixture for the identity pick here and for the classifier's rule in E4-S2.
 */
public final class VanillaOres {
    public static final int FLOOR_Y = -64;
    public static final int LAYERS = 384;

    /** A feature's height shape: uniform, or vanilla's trapezoid with no plateau (a triangle). */
    enum Shape { UNIFORM, TRIANGLE }

    record Feature(String ore, Shape shape, int lowY, int highY, double blocksPerChunk) {
    }

    static final List<Feature> FEATURES = List.of(
            new Feature("minecraft:coal_ore", Shape.UNIFORM, 136, 320, 664.6),
            new Feature("minecraft:coal_ore", Shape.TRIANGLE, 0, 192, 443.0),
            new Feature("minecraft:iron_ore", Shape.TRIANGLE, 80, 384, 558.0),
            new Feature("minecraft:iron_ore", Shape.TRIANGLE, -24, 56, 67.9),
            new Feature("minecraft:iron_ore", Shape.UNIFORM, -64, 72, 19.7),
            new Feature("minecraft:copper_ore", Shape.TRIANGLE, -16, 112, 134.3),
            new Feature("minecraft:gold_ore", Shape.TRIANGLE, -64, 32, 27.2),
            new Feature("minecraft:gold_ore", Shape.UNIFORM, -64, -48, 3.1),
            new Feature("minecraft:redstone_ore", Shape.UNIFORM, -64, 15, 22.4),
            new Feature("minecraft:redstone_ore", Shape.TRIANGLE, -96, -32, 21.4),
            new Feature("minecraft:lapis_ore", Shape.TRIANGLE, -32, 32, 9.9),
            new Feature("minecraft:lapis_ore", Shape.UNIFORM, -64, 64, 19.6),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 6.8),
            new Feature("minecraft:diamond_ore", Shape.UNIFORM, -64, -4, 11.1),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 0.6),
            new Feature("minecraft:diamond_ore", Shape.TRIANGLE, -144, 16, 11.1),
            new Feature("minecraft:emerald_ore", Shape.TRIANGLE, -16, 480, 36.1));

    static final Map<String, Rarity> RARITY = Map.of(
            "minecraft:coal_ore", Rarity.COMMON, "minecraft:iron_ore", Rarity.COMMON, "minecraft:copper_ore", Rarity.COMMON,
            "minecraft:gold_ore", Rarity.UNCOMMON, "minecraft:redstone_ore", Rarity.UNCOMMON, "minecraft:lapis_ore", Rarity.UNCOMMON,
            "minecraft:diamond_ore", Rarity.RARE, "minecraft:emerald_ore", Rarity.RARE);

    /** Emerald's one feature is listed by the mountain biomes only (ADR 0009, BAL-2). */
    static final Map<String, Double> BIOME_SHARE = Map.of("minecraft:emerald_ore", 0.18);

    private VanillaOres() {
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

    /** The same without any rare ore, for #234's "density stays right in a pack with no rare ore". */
    public static List<OrePicker.Candidate> withoutRare() {
        return candidates().stream().filter(c -> c.rarity() != Rarity.RARE).toList();
    }
}
