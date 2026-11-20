package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The adapter's numbers are checked where they enter the pure half, so a bad read fails at start-up, not in a chunk. */
class FeatureSampleTest {
    private static final List<FeatureSample.Target> TIN = List.of(new FeatureSample.Target("mod:tin_ore", true, false));

    private static double[] oneLayer() {
        double[] mass = new double[VanillaOres.LAYERS];
        mass[64] = 3;
        return mass;
    }

    @Test
    void veinsWithNoOriginInsideTheOverworldAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeatureSample("mod:tin", TIN, 2, 5, 1.0, -64, new double[VanillaOres.LAYERS]));
    }

    @Test
    void aShareOutsideZeroToOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FeatureSample("mod:tin", TIN, 2, 5, 0, -64, oneLayer()));
        assertThrows(IllegalArgumentException.class, () -> new FeatureSample("mod:tin", TIN, 2, 5, 1.01, -64, oneLayer()));
    }

    /** ADR 0009 step 3: "more than half"; exactly half is biome-only. */
    @Test
    void allBiomeIsMoreThanHalf() {
        assertFalse(new FeatureSample("mod:tin", TIN, 2, 5, 0.5, -64, oneLayer()).allBiome());
        assertTrue(new FeatureSample("mod:tin", TIN, 2, 5, 0.51, -64, oneLayer()).allBiome());
    }

    @Test
    void theMassIsCopiedInAndOut() {
        double[] mass = oneLayer();
        FeatureSample sample = new FeatureSample("mod:tin", TIN, 2, 5, 1.0, -64, mass);
        mass[64] = 0;
        sample.originMass()[64] = 0;
        assertEquals(3, sample.originMass()[64]);
        assertEquals(10, sample.expectedBlocks(), 1e-12);
    }
}
