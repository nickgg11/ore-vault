package com.orevault.orevault.worldgen;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.ore.ClassifiedOres;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Vault generator places beyond its layer stack, resolved on the main thread (Epic 4 triage, ARCH-E4-2): the
 * identity pickers of the generator's dimension type and the palette from an ore group's id to the block each band places.
 * Immutable, so the worker threads only read it, and built once per generator, so the worker never reads the classifier's
 * {@code volatile} (Reviewer, RV-E4-5).
 *
 * <p>{@link #EMPTY} places nothing at all, no ore and no gravel or clay, and lets the generator take a stack whose bands are
 * not one filler: it is for the gametests that check the bare layer stack. Every Vault gets its inputs from
 * {@link VaultChunkGenerator#forVault}. A pack with no classified ore gets inputs that place gravel and clay only.
 */
public final class OreInputs {
    /** No ore, no gravel, no clay: the bare stack. */
    public static final OreInputs EMPTY = new OreInputs(false, Map.of(), Map.of());

    private final boolean places;
    private final Map<Band, OrePicker> pickers;
    private final Map<String, BandStates> palette;

    /**
     * The blocks one ore group places.
     *
     * @param stone in the stone band
     * @param deepslate in the deepslate band; the stone block for a single-variant ore
     */
    public record BandStates(BlockState stone, BlockState deepslate) {
        public BlockState in(Band band) {
            return band == Band.STONE ? stone : deepslate;
        }
    }

    private OreInputs(boolean places, Map<Band, OrePicker> pickers, Map<String, BandStates> palette) {
        this.places = places;
        EnumMap<Band, OrePicker> copy = new EnumMap<>(Band.class);
        copy.putAll(pickers);
        this.pickers = Collections.unmodifiableMap(copy);
        this.palette = Collections.unmodifiableMap(new TreeMap<>(palette));
    }

    /** Inputs that place ore from these pickers, and gravel and clay. Empty pickers place gravel and clay only. */
    public static OreInputs of(Map<Band, OrePicker> pickers, Map<String, BandStates> palette) {
        return new OreInputs(true, pickers, palette);
    }

    /**
     * From the published classification, on the main thread: the variant's pickers and a state for every classified ore.
     * Every block id in the table came from the block registry at this start, so a missing one is a bug and throws.
     */
    static OreInputs from(ClassifiedOres ores, VaultVariant variant) {
        Map<Band, OrePicker> pickers = ores.pickers(variant);
        Map<String, BandStates> palette = new TreeMap<>();
        for (OreTable.Entry entry : ores.table().entries()) {
            palette.put(entry.groupId(), new BandStates(state(entry.stoneBlock()), state(entry.deepslateBlock())));
        }
        return of(pickers == null ? Map.of() : pickers, palette);
    }

    private static BlockState state(String id) {
        Identifier key = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(key)) {
            throw new IllegalStateException("The ore table names " + id + ", which is not a block");
        }
        return BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
    }

    /** Whether the generator places anything beyond the stack. */
    public boolean places() {
        return places;
    }

    public Map<Band, OrePicker> pickers() {
        return pickers;
    }

    /** The block an ore group places in a band; throws for a group the palette does not hold, which is a bug. */
    public BlockState state(String groupId, Band band) {
        BandStates states = palette.get(groupId);
        if (states == null) {
            throw new IllegalStateException("No block for ore group " + groupId);
        }
        return states.in(band);
    }

    public Map<String, BandStates> palette() {
        return palette;
    }
}
