package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One {@link OrePicker} per band of a dimension type, built from the variant's heights so {@code core.ore} never sees a
 * Vault type (ADR 0001; Architect, ARCH-E4-1). The overworld maps onto the column Y-63..245 in both types (ADR 0009), the
 * expanded type's mineralizable column; in the base type, whose stone band starts at Y=1, mass below the band folds into
 * its lowest slab.
 */
public final class OrePickers {
    private OrePickers() {
    }

    public static Map<Band, OrePicker> forVariant(VaultVariant variant, List<OrePicker.Candidate> candidates) {
        BandBounds deepslate = VaultVariant.EXPANDED.band(Band.DEEPSLATE).orElseThrow();
        int columnFloorY = deepslate.bottomY();
        int columnTopY = VaultVariant.EXPANDED.stoneTopY();
        Map<Band, OrePicker> pickers = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            variant.band(band).ifPresent(bounds -> pickers.put(band, new OrePicker(candidates, columnFloorY, columnTopY,
                    bounds.bottomY(), bounds.topY(), bounds.bottomY() > columnFloorY && variant.band(Band.DEEPSLATE).isEmpty())));
        }
        return Collections.unmodifiableMap(pickers);
    }
}
