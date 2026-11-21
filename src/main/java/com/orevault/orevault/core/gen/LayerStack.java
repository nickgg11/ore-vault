package com.orevault.orevault.core.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    /** As {@link #fromJson(Reader, VaultVariant, Predicate, Predicate, Consumer)}, knowing only the two spellings of air. */
    public static LayerStack fromJson(Reader json, VaultVariant variant, Predicate<String> blockExists, Consumer<String> error) {
        return fromJson(json, variant, blockExists, id -> false, error);
    }

    /**
     * The stack in {@code json} if it is well formed, names only blocks that exist, fills the variant's height exactly and
     * fills each band with one block that can take ore, and otherwise the built-in stack, with one line to {@code error}
     * saying why (§3.1, rule 8). The height is summed as a long before anything is expanded, so a huge thickness is a bad
     * file and not an {@link OutOfMemoryError}.
     *
     * @param cannotFill asked only of blocks that exist: whether a band cannot be filled with it, any air or a fluid. The
     *     caller resolves the block, so {@code cave_air} or a modded air falls back here instead of failing the generator
     *     at restore (S3-RV-2)
     */
    public static LayerStack fromJson(Reader json, VaultVariant variant, Predicate<String> blockExists, Predicate<String> cannotFill,
            Consumer<String> error) {
        try {
            LayerStack stack = parse(json);
            long total = stack.layers.stream().mapToLong(Layer::thickness).sum();
            if (total != variant.height()) {
                throw new IllegalArgumentException("sums to " + total + ", expected the height " + variant.height());
            }
            for (Layer layer : stack.layers) {
                if (!blockExists.test(layer.block)) {
                    throw new IllegalArgumentException("names an unknown block " + layer.block);
                }
            }
            stack.bandFillerProblem(variant, cannotFill).ifPresent(problem -> {
                throw new IllegalArgumentException(problem);
            });
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

    /**
     * Why the stack's mineralizable bands cannot take ore, or empty when they can (§3.1, ADR 0004; Architect, ARCH-E4-8).
     * The generator writes every position of every vein and blob with no check, so every layer of each band the variant has
     * must be one block, not air and not a fluid: the band's filler. A custom file may choose which block (#76). A dirt
     * layer inside the stone band would otherwise turn into ore, and an air layer would hold floating ore. Ids are compared
     * with the default namespace filled in, so {@code stone} and {@code minecraft:stone} are one block (S3-RV-3). The
     * column must fill the variant's height first. A bad file falls back to the built-in stack for this reason too, so a
     * Vault is never refused over it.
     *
     * @param cannotFill whether a band cannot be filled with a block, beyond the two spellings of air this class knows
     */
    public Optional<String> bandFillerProblem(VaultVariant variant, Predicate<String> cannotFill) {
        List<String> column = column();
        if (column.size() != variant.height()) {
            return Optional.of("has " + column.size() + " layers, expected " + variant.height());
        }
        for (Band band : Band.values()) {
            BandBounds bounds = variant.band(band).orElse(null);
            if (bounds == null) {
                continue;
            }
            String filler = normalize(column.get(bounds.bottomY() - variant.minY()));
            if (AIR.equals(filler) || cannotFill.test(filler)) {
                return Optional.of("has air or a fluid, " + filler + ", at Y=" + bounds.bottomY() + " in the " + band + " band");
            }
            for (int y = bounds.bottomY(); y <= bounds.topY(); y++) {
                String block = normalize(column.get(y - variant.minY()));
                if (!block.equals(filler)) {
                    return Optional.of("has " + block + " at Y=" + y + " in the " + band + " band, which is " + filler + " below it");
                }
            }
        }
        return Optional.empty();
    }

    /** {@link #bandFillerProblem(VaultVariant, Predicate)} knowing only the two spellings of air. */
    public Optional<String> bandFillerProblem(VaultVariant variant) {
        return bandFillerProblem(variant, id -> false);
    }

    /** The block filling a band, normalized, checked by {@link #bandFillerProblem}; empty for a band the variant lacks. */
    public Optional<String> filler(VaultVariant variant, Band band) {
        return variant.band(band).map(bounds -> normalize(column().get(bounds.bottomY() - variant.minY())));
    }

    /** An id with the default namespace filled in, as {@code Identifier.parse} reads it; pure string work. */
    static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** The block id at each height, bottom-up: index 0 is the variant's {@code min_y}. */
    public List<String> column() {
        List<String> column = new ArrayList<>();
        for (Layer layer : layers) {
            column.addAll(Collections.nCopies(layer.thickness, layer.block));
        }
        return Collections.unmodifiableList(column);
    }
}
