package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One {@link OrePicker} per band of a dimension type, built from the variant's heights so {@code core.ore} never sees a
 * Vault type (ADR 0001; Architect, ARCH-E4-1). The profiles are read at the overworld's own Y, one to one, in both types
 * (§3.1 "Where each ore sits"; ADR 0009, amended 2026-11-21): vanilla's column Y-64..319 maps onto itself, and a pack whose
 * overworld is taller or deeper maps at its own Y as well. What lies outside a band is that band's no longer; the base
 * type, which has no deepslate band, simply does not have vanilla's deepslate ores until Vault Expansion opens them.
 */
public final class OrePickers {
    private OrePickers() {
    }

    public static Map<Band, OrePicker> forVariant(VaultVariant variant, List<OrePicker.Candidate> candidates) {
        Map<Band, OrePicker> pickers = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            variant.band(band).ifPresent(bounds -> pickers.put(band, new OrePicker(candidates, bounds.bottomY(), bounds.topY())));
        }
        return Collections.unmodifiableMap(pickers);
    }
}
