package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The edge factors the snapshot carries (R-6) are the baseline's own, computed once; 1 where there is nothing to cut. */
class EdgeFactorsTest {
    @Test
    void theyAreTheBaselinesFactorsForEveryBandTheVariantHas() {
        Map<Band, OrePicker> pickers = OrePickers.forVariant(VaultVariant.EXPANDED, VanillaOres.candidates());
        EdgeFactors edges = EdgeFactors.of(OreBaseline.BASE, VaultVariant.EXPANDED, pickers);
        for (Band band : Band.values()) {
            for (Rarity rarity : Rarity.values()) {
                assertEquals(OreBaseline.BASE.edgeFactor(band, rarity, pickers.get(band)), edges.of(band, rarity), 0,
                        band + " " + rarity);
            }
        }
        assertEquals(edges, EdgeFactors.of(OreBaseline.BASE, VaultVariant.EXPANDED, pickers));
    }

    /** No pickers, or a band the variant lacks: 1, so the requests are the bare counts. */
    @Test
    void withNothingToCutEveryFactorIsOne() {
        assertEquals(EdgeFactors.NONE, EdgeFactors.of(OreBaseline.BASE, VaultVariant.BASE, Map.of()));
        EdgeFactors base = EdgeFactors.of(OreBaseline.BASE, VaultVariant.BASE,
                OrePickers.forVariant(VaultVariant.BASE, VanillaOres.candidates()));
        for (Rarity rarity : Rarity.values()) {
            assertEquals(1.0, base.of(Band.DEEPSLATE, rarity));
        }
    }
}
