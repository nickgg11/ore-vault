package com.orevault.orevault.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.gen.Band;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.OreTable;
import com.orevault.orevault.core.ore.Overrides;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The published record holds no Minecraft class, so its shape is a unit test; publication itself is a gametest. */
class ClassifiedOresTest {
    private static OreTable vanilla() {
        return OreTable.build(VanillaOres.samples(), VanillaOres.oreTag(), Overrides.NONE, VanillaOres.FLOOR_Y,
                VanillaOres.LAYERS);
    }

    @Test
    void bothTypesGetAPickerPerBand() {
        ClassifiedOres ores = ClassifiedOres.of(vanilla(), OreClassifier.OWN_SOURCE);
        assertEquals(Set.of(Band.STONE), ores.pickers(VaultVariant.BASE).keySet());
        assertEquals(Set.of(Band.STONE, Band.DEEPSLATE), ores.pickers(VaultVariant.EXPANDED).keySet());
        // Rare in Y1..95 is diamond then emerald by id, emerald about 52% (§3.1), so a draw near 1 is emerald.
        OrePicker.Table rare = ores.pickers(VaultVariant.BASE).get(Band.STONE).table(Rarity.RARE);
        assertEquals("minecraft:emerald_ore", rare.id(rare.pickOre(0.99)));
        assertEquals("minecraft:diamond_ore", rare.id(rare.pickOre(0.01)));
    }

    /** No ore at all: nothing to pick from, and the record says so rather than throwing at start-up. */
    @Test
    void noOreGivesNoPickers() {
        OreTable empty = OreTable.build(List.of(), VanillaOres.oreTag(), Overrides.NONE, VanillaOres.FLOOR_Y,
                VanillaOres.LAYERS);
        ClassifiedOres ores = ClassifiedOres.of(empty, OreClassifier.OWN_SOURCE);
        assertNull(ores.pickers(VaultVariant.BASE));
        assertTrue(ores.pickers().isEmpty());
    }

    @Test
    void thePickerMapsCannotBeChanged() {
        ClassifiedOres ores = ClassifiedOres.of(vanilla(), OreClassifier.OWN_SOURCE);
        assertThrows(UnsupportedOperationException.class, () -> ores.pickers().clear());
        assertThrows(UnsupportedOperationException.class, () -> ores.pickers(VaultVariant.BASE).clear());
    }
}
