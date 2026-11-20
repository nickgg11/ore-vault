package com.orevault.orevault.core.ore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The admin's rarity overrides, §10 {@code ore_classification.overrides}: {@code "modid:block_id=common|uncommon|rare"}
 * strings, applied after classification (ADR 0009 step 8). Each entry is validated on its own and a bad one is rejected
 * with a reason for the server log, never thrown, so one typo costs that entry only. The config's own list check is
 * left at "is a string" for the same reason: NeoForge resets the whole list when one element fails it.
 *
 * <p>An entry naming a block outside {@code #c:ores} is rejected (Epic 4 triage, DEV-E4-4 and RV-E4-4), so
 * {@code minecraft:stone=common} cannot make stone pay: the tag decides what an ore is, the override only its rarity.
 * A block named twice with two rarities applies neither, since no order between config entries is promised.
 */
public final class Overrides {
    public static final Overrides NONE = new Overrides(Map.of(), List.of());

    /** Identifier's characters, with the namespace required: §10's format names the mod. */
    private static final Pattern BLOCK_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private final Map<String, Rarity> byBlock;
    private final List<String> rejected;

    private Overrides(Map<String, Rarity> byBlock, List<String> rejected) {
        this.byBlock = Collections.unmodifiableMap(new TreeMap<>(byBlock));
        this.rejected = List.copyOf(rejected);
    }

    /**
     * @param entries the config's strings, as written
     * @param oreTag the block ids in {@code #c:ores}
     */
    public static Overrides parse(List<String> entries, Set<String> oreTag) {
        Map<String, Rarity> accepted = new TreeMap<>();
        Set<String> clashing = new HashSet<>();
        List<String> rejected = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split("=", -1);
            if (parts.length != 2) {
                rejected.add(quote(entry) + " is not block=rarity");
                continue;
            }
            String block = parts[0].trim();
            String rarityName = parts[1].trim().toLowerCase(Locale.ROOT);
            Rarity rarity = rarityOf(rarityName);
            if (!BLOCK_ID.matcher(block).matches()) {
                rejected.add(quote(entry) + " does not name a block as modid:block_id");
            } else if (rarity == null) {
                rejected.add(quote(entry) + " has no rarity common, uncommon or rare");
            } else if (!oreTag.contains(block)) {
                rejected.add(quote(entry) + " names a block outside #c:ores, which an override cannot make an ore");
            } else if (clashing.contains(block)) {
                rejected.add(quote(entry) + " names " + block + " again");
            } else {
                Rarity earlier = accepted.putIfAbsent(block, rarity);
                if (earlier != null && earlier != rarity) {
                    accepted.remove(block);
                    clashing.add(block);
                    rejected.add(quote(entry) + " gives " + block + " a second rarity, so neither applies");
                }
            }
        }
        return new Overrides(accepted, rejected);
    }

    private static @Nullable Rarity rarityOf(String name) {
        return switch (name) {
            case "common" -> Rarity.COMMON;
            case "uncommon" -> Rarity.UNCOMMON;
            case "rare" -> Rarity.RARE;
            default -> null;
        };
    }

    private static String quote(String entry) {
        return "Override \"" + entry + "\"";
    }

    /** The accepted entries, sorted by block id. */
    public Map<String, Rarity> byBlock() {
        return byBlock;
    }

    /** Why each rejected entry was rejected, in config order, for a WARN each. */
    public List<String> rejected() {
        return rejected;
    }

    public @Nullable Rarity of(String blockId) {
        return byBlock.get(blockId);
    }
}
