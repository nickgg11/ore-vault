package com.orevault.orevault.ore;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ore rarity classification (design spec section 4.2). Runs at server start, scans all
 * registered ore blocks, classifies them via their PlacedFeature vein counts and height
 * ranges, then applies admin overrides from config. In-memory for the session.
 */
public final class OreClassifier {
    public enum OreClass {
        COMMON, UNCOMMON, RARE;

        public com.orevault.orevault.config.OreVaultServerConfig.OreClass toConfigClass() {
            return switch (this) {
                case COMMON -> com.orevault.orevault.config.OreVaultServerConfig.OreClass.COMMON;
                case UNCOMMON -> com.orevault.orevault.config.OreVaultServerConfig.OreClass.UNCOMMON;
                case RARE -> com.orevault.orevault.config.OreVaultServerConfig.OreClass.RARE;
            };
        }
    }

    /** Ore block -> class. Populated at server start. */
    private static final Map<Block, OreClass> CLASSIFICATION = new HashMap<>();
    private static boolean initialized = false;

    private OreClassifier() {
    }

    public static void init(MinecraftServer server) {
        CLASSIFICATION.clear();
        // Scan every registered block that appears in an ore PlacedFeature.
        Map<Block, List<PlacedFeature>> oreFeatures = new HashMap<>();
        try {
            var placedFeatures = server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
            for (var entry : placedFeatures.entrySet()) {
                PlacedFeature placed = entry.getValue();
                ConfiguredFeature<?, ?> configured = placed.feature().value();
                if (configured.feature() instanceof OreFeature && configured.config() instanceof OreConfiguration oreConfig) {
                    for (OreConfiguration.TargetBlockState target : oreConfig.targetStates()) {
                        oreFeatures.computeIfAbsent(target.state().getBlock(), b -> new ArrayList<>()).add(placed);
                    }
                }
            }
        } catch (Exception e) {
            OreVault.LOGGER.warn("Ore Vault: PlacedFeature scan failed, using name-based defaults: {}", e.toString());
        }

        for (Map.Entry<Block, List<PlacedFeature>> entry : oreFeatures.entrySet()) {
            CLASSIFICATION.put(entry.getKey(), classifyFromFeatures(entry.getValue()));
        }
        // Vanilla blocks may not be found by the scan (e.g. not via OreFeature); ensure them.
        putDefault(Blocks.IRON_ORE, OreClass.COMMON);
        putDefault(Blocks.DEEPSLATE_IRON_ORE, OreClass.COMMON);
        putDefault(Blocks.COPPER_ORE, OreClass.COMMON);
        putDefault(Blocks.DEEPSLATE_COPPER_ORE, OreClass.COMMON);
        putDefault(Blocks.COAL_ORE, OreClass.COMMON);
        putDefault(Blocks.DEEPSLATE_COAL_ORE, OreClass.COMMON);
        putDefault(Blocks.GOLD_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.DEEPSLATE_GOLD_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.LAPIS_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.DEEPSLATE_LAPIS_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.REDSTONE_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.DEEPSLATE_REDSTONE_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.NETHER_GOLD_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.NETHER_QUARTZ_ORE, OreClass.UNCOMMON);
        putDefault(Blocks.DIAMOND_ORE, OreClass.RARE);
        putDefault(Blocks.DEEPSLATE_DIAMOND_ORE, OreClass.RARE);
        putDefault(Blocks.EMERALD_ORE, OreClass.RARE);
        putDefault(Blocks.DEEPSLATE_EMERALD_ORE, OreClass.RARE);
        putDefault(Blocks.ANCIENT_DEBRIS, OreClass.RARE);

        // Admin overrides
        for (var override : OreVaultServerConfig.classificationOverrides()) {
            Identifier id = Identifier.tryParse(override.blockId());
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                Block block = BuiltInRegistries.BLOCK.get(id);
                OreClass oreClass = switch (override.oreClass()) {
                    case COMMON -> OreClass.COMMON;
                    case UNCOMMON -> OreClass.UNCOMMON;
                    case RARE -> OreClass.RARE;
                };
                CLASSIFICATION.put(block, oreClass);
                OreVault.LOGGER.info("Ore Vault: classification override {} -> {}", override.blockId(), oreClass);
            } else {
                OreVault.LOGGER.warn("Ore Vault: classification override for unknown block {}", override.blockId());
            }
        }
        initialized = true;
        OreVault.LOGGER.info("Ore Vault: classified {} ore blocks ({} common, {} uncommon, {} rare)",
                CLASSIFICATION.size(),
                CLASSIFICATION.values().stream().filter(c -> c == OreClass.COMMON).count(),
                CLASSIFICATION.values().stream().filter(c -> c == OreClass.UNCOMMON).count(),
                CLASSIFICATION.values().stream().filter(c -> c == OreClass.RARE).count());
    }

    private static void putDefault(Block block, OreClass oreClass) {
        CLASSIFICATION.putIfAbsent(block, oreClass);
    }

    private static OreClass classifyFromFeatures(List<PlacedFeature> features) {
        int totalCount = 0;
        int minMaxHeight = Integer.MAX_VALUE;
        int maxMaxHeight = Integer.MIN_VALUE;
        int maxMinHeight = Integer.MAX_VALUE;
        int minMinHeight = Integer.MIN_VALUE;
        for (PlacedFeature placed : features) {
            for (PlacementModifier modifier : placed.placement()) {
                if (modifier instanceof CountPlacement count) {
                    totalCount += count.count();
                } else if (modifier instanceof HeightRangePlacement height) {
                    var h = height.height();
                    int mn = h.minInclusive();
                    int mx = h.maxInclusive();
                    minMaxHeight = Math.min(minMaxHeight, mx);
                    maxMaxHeight = Math.max(maxMaxHeight, mx);
                    maxMinHeight = Math.min(maxMinHeight, mn);
                    minMinHeight = Math.max(minMinHeight, mn);
                }
            }
        }
        int maxHeight = maxMaxHeight == Integer.MIN_VALUE ? 320 : maxMaxHeight;
        int minHeight = maxMinHeight == Integer.MAX_VALUE ? -64 : maxMinHeight;
        int range = maxHeight - minHeight;
        if (totalCount <= 4 && maxHeight <= 32) {
            return OreClass.RARE;
        }
        if (totalCount >= 15 && range >= 128) {
            return OreClass.COMMON;
        }
        return OreClass.UNCOMMON;
    }

    public static OreClass classify(Block block) {
        return CLASSIFICATION.getOrDefault(block, OreClass.UNCOMMON);
    }

    public static OreClass classify(Block block, OreClass fallback) {
        return CLASSIFICATION.getOrDefault(block, fallback);
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /** All classified ore blocks for a given class. */
    public static List<Block> oresOf(OreClass oreClass) {
        List<Block> out = new ArrayList<>();
        CLASSIFICATION.forEach((block, cls) -> {
            if (cls == oreClass && !block.defaultBlockState().isAir()) {
                out.add(block);
            }
        });
        return out;
    }

    /** True when the block is any classified ore. */
    public static boolean isOre(Block block) {
        return CLASSIFICATION.containsKey(block);
    }

    /** Matches the deepslate variant of a stone ore, when one exists. */
    public static Block deepslateVariant(Block ore) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(ore);
        if (id == null || !id.getNamespace().equals("minecraft")) {
            return null;
        }
        Identifier deep = Identifier.fromNamespaceAndPath("minecraft", "deepslate_" + id.getPath());
        if (BuiltInRegistries.BLOCK.containsKey(deep)) {
            return BuiltInRegistries.BLOCK.get(deep);
        }
        return null;
    }

    /** Ore tag for dust fallback lookups (integration module uses these names). */
    public static String oreMaterialName(Block ore) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(ore);
        if (id == null) {
            return "unknown";
        }
        String path = id.getPath();
        path = path.replace("deepslate_", "").replace("nether_", "");
        path = path.replace("_ore", "");
        return path;
    }
}
