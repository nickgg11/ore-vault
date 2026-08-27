package com.orevault.orevault.ore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Session-scoped ore rarity classification (§11): scans the {@code #c:ores}
 * block tag at every server start, reads each ore's registered
 * {@link PlacedFeature}s to extract vein count + height range, applies the
 * §11 thresholds, and finally layers admin overrides (§10) on top.
 *
 * <p>The classification is kept in memory only — it is never cached to disk —
 * and is deliberately tolerant: ores without usable placement data fall back
 * to {@link Rarity#UNCOMMON}.</p>
 *
 * <p>Note: MC 26.1 has no {@code OreBlock} class anymore (ores are plain
 * {@code DropExperienceBlock}s), so the scan uses the {@code #c:ores} tag
 * instead; the placement metrics are read through access transformers since
 * 26.1 exposes no getters for them.</p>
 */
public final class OreClassifier {

    public enum Rarity {
        COMMON,
        UNCOMMON,
        RARE
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Block> ORES_TAG =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores"));

    private static final Map<Identifier, Rarity> CLASSIFICATION = new ConcurrentHashMap<>();

    private OreClassifier() {
    }

    /** Clears the session classification (used on shutdown / reload). */
    public static void clear() {
        CLASSIFICATION.clear();
    }

    /**
     * Pure §11 threshold rule, kept static so it can be unit-tested with fake
     * (count, height) tuples:
     * <ul>
     *   <li>count ≤ 4 AND max height ≤ Y=32 → {@link Rarity#RARE}</li>
     *   <li>count ≥ 15 AND height range ≥ 128 blocks → {@link Rarity#COMMON}</li>
     *   <li>everything else → {@link Rarity#UNCOMMON}</li>
     * </ul>
     */
    public static Rarity classify(int veinCount, int minY, int maxY) {
        if (veinCount <= 4 && maxY <= 32) {
            return Rarity.RARE;
        }
        if (veinCount >= 15 && maxY - minY >= 128) {
            return Rarity.COMMON;
        }
        return Rarity.UNCOMMON;
    }

    /**
     * Applies an admin override (key {@code "modid:block=common|uncommon|rare"},
     * §10) on top of the automatic classification. Unknown or invalid values
     * fall back to the automatic result.
     */
    public static Rarity applyOverride(Rarity automatic, String blockId, Map<String, String> overrides) {
        return switch (overrides.get(blockId)) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            case null, default -> automatic;
        };
    }

    /**
     * Rebuilds the session classification from the given registries. Runs at
     * every server start (never cached to disk).
     */
    public static void rebuild(HolderLookup.Provider registries, Map<String, String> overrides) {
        HolderLookup.RegistryLookup<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        HolderLookup.RegistryLookup<PlacedFeature> placedFeatures = registries.lookupOrThrow(Registries.PLACED_FEATURE);
        HolderSet.Named<Block> oreBlocks = blocks.getOrThrow(ORES_TAG);

        // Pass 1: aggregate (vein count, height range) per ore block from every
        // placed feature whose configuration targets that block.
        Map<Block, Metrics> metricsByBlock = new HashMap<>();
        for (Holder.Reference<PlacedFeature> reference : placedFeatures.listElements().toList()) {
            PlacedFeature placedFeature = reference.value();
            if (!(placedFeature.feature().value().config() instanceof OreConfiguration ore)) {
                continue;
            }
            Metrics metrics = Metrics.from(placedFeature.placement());
            if (!metrics.hasData()) {
                continue;
            }
            for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                metricsByBlock.merge(target.state.getBlock(), metrics, Metrics::merge);
            }
        }

        // Pass 2: classify every ore in the tag, then apply overrides last.
        Map<Identifier, Rarity> result = new HashMap<>();
        for (Holder<Block> holder : oreBlocks) {
            Block block = holder.value();
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (id == null) {
                continue; // not a registered block; ignore
            }
            Metrics metrics = metricsByBlock.get(block);
            Rarity automatic = metrics == null ? Rarity.UNCOMMON : classify(metrics.veinCount, metrics.minY, metrics.maxY);
            result.put(id, applyOverride(automatic, id.toString(), overrides));
        }

        CLASSIFICATION.clear();
        CLASSIFICATION.putAll(result);
        LOGGER.info("Ore rarity classification rebuilt: {} ore blocks, {} override(s) applied", result.size(), overrides.size());
    }

    /** Session rarity of a block state; defaults to {@link Rarity#UNCOMMON}. */
    public static Rarity getRarity(BlockState state) {
        return getRarity(state.getBlock());
    }

    /** Session rarity of a block; defaults to {@link Rarity#UNCOMMON}. */
    public static Rarity getRarity(Block block) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? Rarity.UNCOMMON : CLASSIFICATION.getOrDefault(id, Rarity.UNCOMMON);
    }

    /** Aggregated placement metrics for one ore block. */
    private record Metrics(int veinCount, int minY, int maxY) {

        boolean hasData() {
            return veinCount > 0 && minY != Integer.MAX_VALUE && maxY != Integer.MIN_VALUE;
        }

        /** Extracts (vein count, height range) from a placed feature's modifiers. */
        static Metrics from(List<PlacementModifier> modifiers) {
            int count = 0;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (PlacementModifier modifier : modifiers) {
                if (modifier instanceof CountPlacement countPlacement) {
                    count = Math.max(count, countPlacement.count.maxInclusive());
                } else if (modifier instanceof HeightRangePlacement range) {
                    int[] bounds = boundsOf(range.height);
                    if (bounds != null) {
                        minY = Math.min(minY, bounds[0]);
                        maxY = Math.max(maxY, bounds[1]);
                    }
                }
            }
            return new Metrics(count, minY, maxY);
        }

        /** Combines metrics from several placed features of the same ore block. */
        static Metrics merge(Metrics a, Metrics b) {
            return new Metrics(
                    Math.max(a.veinCount, b.veinCount),
                    Math.min(a.minY, b.minY),
                    Math.max(a.maxY, b.maxY)
            );
        }

        /** Absolute [min, max] of a height provider, or {@code null} if not absolute. */
        private static int[] boundsOf(HeightProvider provider) {
            if (provider instanceof ConstantHeight constant) {
                return anchorBounds(constant.getValue());
            }
            if (provider instanceof UniformHeight uniform) {
                int[] min = anchorBounds(uniform.minInclusive);
                int[] max = anchorBounds(uniform.maxInclusive);
                return min != null && max != null ? new int[] { min[0], max[0] } : null;
            }
            return null; // trapezoid / biased / non-absolute providers carry no fixed range
        }

        private static int[] anchorBounds(VerticalAnchor anchor) {
            return anchor instanceof VerticalAnchor.Absolute absolute ? new int[] { absolute.y() } : null;
        }
    }
}
