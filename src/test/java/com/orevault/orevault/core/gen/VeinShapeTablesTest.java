package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Locale;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * The committed tables are exactly what a fresh fixed-seed run of the port gives (ARCH-E4-6: with a pinned algorithm
 * the regeneration is exact, not within a tolerance). On a mismatch the failure prints the arrays to paste into
 * {@link VeinShapeTables}.
 */
class VeinShapeTablesTest {
    static final int ROLLS = 10_000;
    static final long SEED = 0x0E7EA1L;

    @Test
    void theTablesMatchAFreshRun() {
        double[] meanBlocks = new double[VeinShapeTables.MAX_SIZE + 1];
        double[] boxHeight = new double[VeinShapeTables.MAX_SIZE + 1];
        for (int size = 1; size <= VeinShapeTables.MAX_SIZE; size++) {
            SplittableRandom random = GenRandom.of(SEED + size);
            long blocks = 0;
            double weightedHeight = 0;
            for (int i = 0; i < ROLLS; i++) {
                VeinShape.Rolled vein = VeinShape.roll(size, random);
                blocks += vein.blocks();
                weightedHeight += (double) vein.blocks() * vein.sizeY();
            }
            meanBlocks[size] = round(blocks / (double) ROLLS);
            boxHeight[size] = blocks == 0 ? 0 : round(weightedHeight / blocks);
        }
        boolean same = true;
        for (int size = 1; size <= VeinShapeTables.MAX_SIZE; size++) {
            same &= meanBlocks[size] == VeinShapeTables.MEAN_BLOCKS[size];
            same &= boxHeight[size] == VeinShapeTables.BLOCK_WEIGHTED_BOX_HEIGHT[size];
        }
        if (!same) {
            fail("VeinShapeTables differs from a fresh run. Paste:\nMEAN_BLOCKS = " + format(meanBlocks)
                    + "\nBLOCK_WEIGHTED_BOX_HEIGHT = " + format(boxHeight));
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String format(double[] values) {
        StringBuilder out = new StringBuilder("{\n");
        for (int i = 0; i < values.length; i++) {
            out.append(i % 8 == 0 ? "            " : " ").append(String.format(Locale.ROOT, "%.3f,", values[i]));
            if (i % 8 == 7 || i == values.length - 1) {
                out.append('\n');
            }
        }
        return out.append("    };").toString();
    }
}
