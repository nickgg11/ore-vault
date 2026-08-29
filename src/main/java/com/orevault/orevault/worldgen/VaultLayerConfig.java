package com.orevault.orevault.worldgen;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.orevault.orevault.OreVault;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Data-driven vault layer stack (#76): a bottom-up list of
 * {@code {block, thickness}} pairs, jamd-style, loaded from
 * {@code data/orevault/worldgen/vault_layers/<dimension-type-path>.json}
 * (e.g. {@code ore_vault.json}). The layer list must sum to the dimension's
 * height; any missing/malformed config falls back to the classic §3.1 stack.
 *
 * <p>The first air layer defines the entry surface ({@link #firstAirY}) and
 * the first stone layer defines the ore band
 * ({@link #stoneBandBottom()}/{@link #stoneBandTop()}) for the chunk
 * generator, so the whole vault geology is re-tunable per dimension type
 * without touching code — including the expanded type's extra depth (#59).</p>
 */
public final class VaultLayerConfig {

    /** One layer: a block placed {@code thickness} times, bottom-up from {@code minY}. */
    public record Layer(Block block, int thickness) {
        public Layer {
            if (thickness < 1) {
                throw new IllegalArgumentException("Layer thickness must be >= 1");
            }
        }
    }

    /** Classic §3.1 stack thicknesses: grass surface over a 4-block dirt band. */
    public static final int DEFAULT_GRASS_THICKNESS = 1;
    public static final int DEFAULT_DIRT_THICKNESS = 4;
    public static final int DEFAULT_BEDROCK_THICKNESS = 1;
    /** Air layer on top of the solid fill (§3.1: 64-block open working space + soil). */
    public static final int DEFAULT_AIR_THICKNESS = 69;

    private final int minY;
    private final int height;
    private final List<Layer> layers; // bottom-up, sums to height
    private final int firstAirY; // bottom Y of the first air layer, -1 if none
    private final int stoneBandBottom; // inclusive bottom of the first stone layer, -1 if none
    private final int stoneBandTop; // exclusive top of the first stone layer, -1 if none

    public VaultLayerConfig(int minY, int height, List<Layer> layers) {
        int total = layers.stream().mapToInt(Layer::thickness).sum();
        if (total != height) {
            throw new IllegalArgumentException("Vault layer stack sums to " + total + ", expected dimension height " + height);
        }
        this.minY = minY;
        this.height = height;
        this.layers = List.copyOf(layers);

        int y = minY;
        int air = -1;
        int stoneBottom = -1;
        int stoneTop = -1;
        for (Layer layer : layers) {
            if (layer.block() == Blocks.STONE && stoneBottom < 0) {
                stoneBottom = y;
                stoneTop = y + layer.thickness();
            }
            if (layer.block() == Blocks.AIR && air < 0) {
                air = y;
            }
            y += layer.thickness();
        }
        this.firstAirY = air;
        this.stoneBandBottom = stoneBottom;
        this.stoneBandTop = stoneTop;
    }

    /**
     * Loads the layer stack for the given dimension type from the server's
     * data packs; falls back to the built-in classic stack when the file is
     * missing, malformed, or doesn't sum to the dimension height.
     */
    public static VaultLayerConfig load(MinecraftServer server, ResourceKey<DimensionType> type, int minY, int height) {
        Identifier id = Identifier.fromNamespaceAndPath(
                type.identifier().getNamespace(),
                "worldgen/vault_layers/" + type.identifier().getPath() + ".json"
        );
        try {
            var resource = server.getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (Reader reader = resource.get().openAsReader()) {
                    VaultLayerConfig parsed = parse(reader, minY, height);
                    OreVault.LOGGER.info("Loaded vault layer stack {}: {} layers, entry surface Y={}, stone band Y={}..{}",
                            id, parsed.layers().size(), parsed.firstAirY(), parsed.stoneBandBottom(), parsed.stoneBandTop());
                    return parsed;
                }
            } else {
                OreVault.LOGGER.warn("Missing vault layer config {}; using the built-in default stack", id);
            }
        } catch (IOException | RuntimeException e) {
            OreVault.LOGGER.error("Failed to load vault layer config {}; using the built-in default stack", id, e);
        }
        return defaults(minY, height);
    }

    /** Parses a layer-stack JSON document; throws on malformed input. */
    public static VaultLayerConfig parse(Reader reader, int minY, int height) throws IOException {
        JsonObject root = GsonHelper.parse(reader);
        JsonArray array = GsonHelper.getAsJsonArray(root, "layers");
        List<Layer> layers = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject layerJson = GsonHelper.convertToJsonObject(element, "layer");
            String blockId = GsonHelper.getAsString(layerJson, "block");
            int thickness = GsonHelper.getAsInt(layerJson, "thickness");
            Block block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(blockId))
                    .orElseThrow(() -> new IllegalArgumentException("Unknown block in vault layer config: " + blockId));
            layers.add(new Layer(block, thickness));
        }
        return new VaultLayerConfig(minY, height, layers);
    }

    /** The classic §3.1 stack: bedrock, deepslate to Y=0, stone, dirt, grass, air. */
    public static VaultLayerConfig defaults(int minY, int height) {
        int deepslate = -minY - DEFAULT_BEDROCK_THICKNESS;
        int soil = DEFAULT_GRASS_THICKNESS + DEFAULT_DIRT_THICKNESS;
        int stone = height - DEFAULT_BEDROCK_THICKNESS - deepslate - soil - DEFAULT_AIR_THICKNESS;
        if (stone < 1) {
            throw new IllegalArgumentException(
                    "Height " + height + " with minY " + minY + " is too small for the default vault layer stack");
        }
        return new VaultLayerConfig(minY, height, List.of(
                new Layer(Blocks.BEDROCK, DEFAULT_BEDROCK_THICKNESS),
                new Layer(Blocks.DEEPSLATE, deepslate),
                new Layer(Blocks.STONE, stone),
                new Layer(Blocks.DIRT, DEFAULT_DIRT_THICKNESS),
                new Layer(Blocks.GRASS_BLOCK, DEFAULT_GRASS_THICKNESS),
                new Layer(Blocks.AIR, DEFAULT_AIR_THICKNESS)
        ));
    }

    public int minY() {
        return minY;
    }

    public int height() {
        return height;
    }

    public List<Layer> layers() {
        return layers;
    }

    /** Bottom Y of the first air layer (the entry surface), or -1 if the stack has no air. */
    public int firstAirY() {
        return firstAirY;
    }

    /** Inclusive bottom of the first stone layer, or -1 if none. */
    public int stoneBandBottom() {
        return stoneBandBottom;
    }

    /** Exclusive top of the first stone layer, or -1 if none. */
    public int stoneBandTop() {
        return stoneBandTop;
    }

    public boolean hasStoneBand() {
        return stoneBandBottom >= 0 && stoneBandTop > stoneBandBottom;
    }

    /** The generated block for an absolute Y coordinate (air outside the world bounds). */
    public BlockState blockAt(int worldY) {
        if (worldY < minY || worldY >= minY + height) {
            return Blocks.AIR.defaultBlockState();
        }
        int offset = worldY - minY;
        for (Layer layer : layers) {
            if (offset < layer.thickness()) {
                return layer.block().defaultBlockState();
            }
            offset -= layer.thickness();
        }
        return Blocks.AIR.defaultBlockState(); // unreachable: layers sum to height
    }
}
