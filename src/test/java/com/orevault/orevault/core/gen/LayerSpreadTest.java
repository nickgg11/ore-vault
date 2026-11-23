package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.ore.HeightProfile;
import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import com.orevault.orevault.core.ore.VanillaOres;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The one source of where a request's blocks sit by layer (§3.1 "How the baseline is checked"; ADR 0009, amended
 * 2026-11-21), on hand-made profiles whose answers are known, so a broken spread fails here and not only through the
 * edge factor, the clip and the slab checks that read it (QA, Q-8).
 */
class LayerSpreadTest {
    private static final BandBounds STONE = new BandBounds(1, 95);
    private static final int REACH = VeinShape.KERNEL_REACH;

    /** One ore whose whole profile is the layers {@code fromY..toY}, in the base stone band. */
    private static OrePicker oneOreAt(int fromY, int toY) {
        double[] mass = new double[VanillaOres.LAYERS];
        for (int y = fromY; y <= toY; y++) {
            mass[y - VanillaOres.FLOOR_Y] = 1;
        }
        return new OrePicker(List.of(new OrePicker.Candidate("test:ore", Rarity.COMMON, 1,
                new HeightProfile(VanillaOres.FLOOR_Y, mass))), STONE.bottomY(), STONE.topY());
    }

    /** A fractional size is its two neighbours with the probabilities the placer draws them with. */
    @Test
    void aFractionalSizeBlendsItsNeighboursKernels() {
        VeinRequest request = new VeinRequest(Band.STONE, Rarity.COMMON, 1, new double[] {9.25}, 1, 95, false);
        double[] kernel = LayerSpread.kernel(request);
        double[] nine = VeinShape.layerKernel(9);
        double[] ten = VeinShape.layerKernel(10);
        for (int i = 0; i < kernel.length; i++) {
            assertEquals(0.75 * nine[i] + 0.25 * ten[i], kernel[i], 1e-12, "offset " + (i - REACH));
        }
        assertEquals(ClipFactors.meanBlocks(request), LayerSpread.sum(kernel), 0.01, "the kernel holds a vein's blocks");
    }

    @Test
    void aBandsVeinsAverageTheirSizes() {
        double[] kernel = LayerSpread.kernel(new OreBaseline.Veins(3, 8, 10));
        for (int i = 0; i < kernel.length; i++) {
            double mean = (VeinShape.layerKernel(8)[i] + VeinShape.layerKernel(9)[i] + VeinShape.layerKernel(10)[i]) / 3;
            assertEquals(mean, kernel[i], 1e-12);
        }
    }

    /** An ore far from both edges keeps every block, centred on its layers. */
    @Test
    void anOreFarFromTheEdgesKeepsEverything() {
        double[] kernel = LayerSpread.kernel(new OreBaseline.Veins(1, 9, 9));
        double[] perVein = LayerSpread.perVein(oneOreAt(50, 50).table(Rarity.COMMON), kernel, STONE);
        assertEquals(1.0, LayerSpread.kept(perVein, kernel), 1e-12);
        for (int offset = -REACH; offset <= REACH; offset++) {
            assertEquals(kernel[offset + REACH], perVein[50 + offset - STONE.bottomY()], 1e-12, "offset " + offset);
        }
        assertEquals(0, perVein[50 - REACH - 1 - STONE.bottomY()]);
        assertEquals(0, perVein[50 + REACH + 1 - STONE.bottomY()]);
    }

    /** An ore all at the band's floor loses the blocks below its middle: what the edge factor makes up. */
    @Test
    void anOreAtTheFloorLosesTheBlocksBelowIt() {
        double[] kernel = LayerSpread.kernel(new OreBaseline.Veins(1, 9, 9));
        double[] perVein = LayerSpread.perVein(oneOreAt(1, 1).table(Rarity.COMMON), kernel, STONE);
        double above = 0;
        for (int offset = 0; offset <= REACH; offset++) {
            above += kernel[offset + REACH];
        }
        assertEquals(above / LayerSpread.sum(kernel), LayerSpread.kept(perVein, kernel), 1e-12);
        assertTrue(LayerSpread.kept(perVein, kernel) < 1);
    }

    /** The band holds the request's whole closed form, count times mean blocks, however much its edges cut. */
    @Test
    void thePlacedLayersHoldTheClosedForm() {
        VeinRequest request = new VeinRequest(Band.STONE, Rarity.COMMON, 7.5, new double[] {8, 9, 10}, 1, 95, false);
        double[] placed = LayerSpread.placedByLayer(request, oneOreAt(1, 3), STONE);
        assertEquals(7.5 * ClipFactors.meanBlocks(request), LayerSpread.sum(placed), 1e-9);
        assertEquals(STONE.layers(), placed.length);
    }

    @Test
    void anEmptyKernelKeepsEverythingAndARegionOutsideTheBandIsRefused() {
        assertEquals(1.0, LayerSpread.kept(new double[STONE.layers()], new double[2 * REACH + 1]));
        VeinRequest outside = new VeinRequest(Band.STONE, Rarity.COMMON, 1, new double[] {9}, 90, 120, false);
        assertThrows(IllegalArgumentException.class, () -> LayerSpread.placedByLayer(outside, oneOreAt(50, 50), STONE));
    }
}
