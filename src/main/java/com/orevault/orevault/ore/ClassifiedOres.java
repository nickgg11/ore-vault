package com.orevault.orevault.ore;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.OrePickers;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.OreTable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the classifier publishes for the session (ADR 0009 "Publication to the generator threads"): the table, and the
 * identity pickers of both dimension types built from it, so the generator's worker threads only read. Immutable: the
 * table is, each picker is, and the maps are unmodifiable copies. Built on the main thread before any Vault is restored.
 *
 * @param table the classification
 * @param source which overworld it was classified against, for the log and the S44 readout (Epic 4 triage ruling 6)
 * @param pickers per dimension type, one picker per band it has; empty when the pack has no ore at all
 */
public record ClassifiedOres(OreTable table, String source, Map<VaultVariant, Map<Band, OrePicker>> pickers) {
    public ClassifiedOres {
        Map<VaultVariant, Map<Band, OrePicker>> copy = new EnumMap<>(VaultVariant.class);
        copy.putAll(pickers);
        pickers = Collections.unmodifiableMap(copy);
    }

    static ClassifiedOres of(OreTable table, String source) {
        Map<VaultVariant, Map<Band, OrePicker>> pickers = new EnumMap<>(VaultVariant.class);
        if (!table.isEmpty()) {
            for (VaultVariant variant : VaultVariant.values()) {
                pickers.put(variant, OrePickers.forVariant(variant, table.candidates()));
            }
        }
        return new ClassifiedOres(table, source, pickers);
    }

    /** The type's pickers, or {@code null} when no ore was classified (the classifier has logged an ERROR then). */
    public @Nullable Map<Band, OrePicker> pickers(VaultVariant variant) {
        return pickers.get(variant);
    }
}
