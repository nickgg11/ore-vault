package com.orevault.orevault.core.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A Vault's terrain before ore: a bottom-up list of blocks and thicknesses (§3.1), jamd-style. It holds block ids as
 * strings, so the stack and its validation run in JUnit and the generator resolves the ids once, on the main thread. The
 * JSON is read here for the same reason, so a malformed file is a unit test.
 *
 * @param layers bottom-up
 */
public record LayerStack(List<Layer> layers) {
    public static final String AIR = "minecraft:air";

    private static final String BEDROCK = "minecraft:bedrock";
    private static final String DEEPSLATE = "minecraft:deepslate";
    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";
    private static final String GRASS = "minecraft:grass_block";

    private static final List<Layer> SURFACE = List.of(new Layer(DIRT, 4), new Layer(GRASS, 1), new Layer(AIR, 69));
    private static final LayerStack BASE = new LayerStack(stack(new Layer(BEDROCK, 1), new Layer(STONE, 245)));
    private static final LayerStack EXPANDED = new LayerStack(
            stack(new Layer(BEDROCK, 1), new Layer(DEEPSLATE, 63), new Layer(STONE, 246)));

    public LayerStack {
        layers = List.copyOf(layers);
    }

    /** One block, {@code thickness} times over. */
    public record Layer(String block, int thickness) {
        public Layer {
            if (thickness < 1) {
                throw new IllegalArgumentException("Layer " + block + " has thickness " + thickness);
            }
        }
    }

    private static List<Layer> stack(Layer... below) {
        List<Layer> layers = new ArrayList<>(List.of(below));
        layers.addAll(SURFACE);
        return layers;
    }

    /** The stack §3.1 states, used when a variant's file is missing or bad. */
    public static LayerStack builtIn(VaultVariant variant) {
        return switch (variant) {
            case BASE -> BASE;
            case EXPANDED -> EXPANDED;
        };
    }

    /**
     * The stack in {@code json} if it is well formed, names only blocks that exist and fills the variant's height exactly,
     * and otherwise the built-in stack, with one line to {@code error} saying why (§3.1, rule 8).
     */
    public static LayerStack fromJson(Reader json, VaultVariant variant, Predicate<String> blockExists, Consumer<String> error) {
        try {
            LayerStack stack = parse(json);
            int total = stack.column().size();
            if (total != variant.height()) {
                throw new IllegalArgumentException("sums to " + total + ", expected the height " + variant.height());
            }
            for (Layer layer : stack.layers) {
                if (!blockExists.test(layer.block)) {
                    throw new IllegalArgumentException("names an unknown block " + layer.block);
                }
            }
            return stack;
        } catch (RuntimeException e) {
            error.accept("Layer stack for " + variant.id() + " is bad, using the built-in stack: " + e.getMessage());
            return builtIn(variant);
        }
    }

    private static LayerStack parse(Reader json) {
        List<Layer> layers = new ArrayList<>();
        for (JsonElement element : JsonParser.parseReader(json).getAsJsonObject().getAsJsonArray("layers")) {
            JsonObject layer = element.getAsJsonObject();
            layers.add(new Layer(layer.get("block").getAsString(), layer.get("thickness").getAsInt()));
        }
        return new LayerStack(layers);
    }

    /** The block id at each height, bottom-up: index 0 is the variant's {@code min_y}. */
    public List<String> column() {
        List<String> column = new ArrayList<>();
        for (Layer layer : layers) {
            column.addAll(Collections.nCopies(layer.thickness, layer.block));
        }
        return Collections.unmodifiableList(column);
    }

    /** The column index of the lowest air block, or -1 for a stack with none. */
    public int firstAirIndex() {
        return column().indexOf(AIR);
    }
}
