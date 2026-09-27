package com.orevault.orevault.ore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.orevault.orevault.ore.OreClassifier.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ores a Vault may generate, grouped by rarity and ready to place (§3.1).
 *
 * <p>Built once from {@link OreClassifier}'s scan, which already sorts every registered ore in
 * {@code #c:ores} by its real placement data. The generator picks from this rather than from a list
 * of ore ids, so a kitchen-sink pack's modded ores generate with no per-mod work and the thing that
 * decides what an ore is <em>worth</em> is the same thing that decides how often it appears.</p>
 *
 * <h2>Why this is a snapshot and not a live lookup</h2>
 *
 * <p>Chunk generation runs on a background executor (CLAUDE.md item 1, §11). An immutable instance
 * published once after the server-start scan is safe to read from there; a map still being filled
 * is not. Entries are sorted by block id so a team's Vault generates identically on every load, and
 * {@link Entry} carries the pre-resolved {@link BlockState} and depth so generation allocates
 * nothing per vein.</p>
 */
public final class VaultOreTable {

    /**
     * One placeable ore.
     *
     * @param normalizedDepth where it belongs in a Vault's column, 0 at bedrock and 1 at the
     *                        surface, derived from its overworld placement range
     */
    public record Entry(Identifier id, BlockState state, Rarity rarity, double normalizedDepth) {
    }

    /** No ores classified — before the first server-start scan, or a pack with an empty ore tag. */
    public static final VaultOreTable EMPTY = new VaultOreTable(List.of());

    private final Map<Rarity, List<Entry>> byRarity;
    private final int size;

    public VaultOreTable(List<Entry> entries) {
        Map<Rarity, List<Entry>> grouped = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) {
            grouped.put(rarity, new ArrayList<>());
        }
        for (Entry entry : entries) {
            grouped.get(entry.rarity()).add(entry);
        }
        Map<Rarity, List<Entry>> sorted = new EnumMap<>(Rarity.class);
        for (Map.Entry<Rarity, List<Entry>> group : grouped.entrySet()) {
            List<Entry> list = group.getValue();
            list.sort(Comparator.comparing(entry -> entry.id().toString()));
            sorted.put(group.getKey(), List.copyOf(list));
        }
        this.byRarity = Map.copyOf(sorted);
        this.size = entries.size();
    }

    /** Every ore of the given rarity, in a stable order. Empty when the pack has none. */
    public List<Entry> of(Rarity rarity) {
        return byRarity.getOrDefault(rarity, List.of());
    }

    /**
     * The ores of {@code rarity}, or the nearest rarity that has any.
     *
     * <p>A pack with no rare ore at all is not a reason to generate nothing when the roll comes up
     * rare — it is a reason to generate something else. Falling back keeps the total density right
     * in a pack whose ore tag is thinner than vanilla's.</p>
     */
    public List<Entry> ofOrFallback(Rarity rarity) {
        List<Entry> exact = of(rarity);
        if (!exact.isEmpty()) {
            return exact;
        }
        for (Rarity other : Rarity.values()) {
            List<Entry> candidates = of(other);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return List.of();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Total ores classified, for the server-start log line. */
    public int size() {
        return size;
    }
}
