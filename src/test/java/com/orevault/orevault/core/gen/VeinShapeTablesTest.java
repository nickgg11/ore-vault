package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * The committed tables are exactly what a fresh fixed-seed run of the port gives (ARCH-E4-6: with a pinned algorithm
 * the regeneration is exact, not within a tolerance). On a mismatch the failure prints the arrays to paste into
 * {@link VeinShapeTables}. The layer kernel (ADR 0009, amended 2026-11-21, D2) is regenerated the same way, from the
 * same rolls, and no roll may place a block farther from its box middle than {@link VeinShapeTables#KERNEL_REACH}.
 */
class VeinShapeTablesTest {
    static final int ROLLS = 10_000;
    static final long SEED = 0x0E7EA1L;

    @Test
    void theTablesMatchAFreshRun() {
        double[] meanBlocks = new double[VeinShapeTables.MAX_SIZE + 1];
        double[] boxHeight = new double[VeinShapeTables.MAX_SIZE + 1];
        int reach = VeinShapeTables.KERNEL_REACH;
        double[][] kernel = new double[VeinShapeTables.MAX_SIZE + 1][2 * reach + 1];
        int widest = 0;
        for (int size = 1; size <= VeinShapeTables.MAX_SIZE; size++) {
            SplittableRandom random = GenRandom.of(SEED + size);
            long blocks = 0;
            double weightedHeight = 0;
            long[] byOffset = new long[2 * reach + 1];
            for (int i = 0; i < ROLLS; i++) {
                VeinShape.Rolled vein = VeinShape.roll(size, random);
                blocks += vein.blocks();
                weightedHeight += (double) vein.blocks() * vein.sizeY();
                // The placer puts the box's layer y at middle - (sizeY - 1) / 2 + y, so the offset is from that middle.
                int middle = (vein.sizeY() - 1) / 2;
                for (int y = 0; y < vein.sizeY(); y++) {
                    int inLayer = 0;
                    for (int x = 0; x < vein.sizeX(); x++) {
                        for (int z = 0; z < vein.sizeZ(); z++) {
                            inLayer += vein.isSet(x, y, z) ? 1 : 0;
                        }
                    }
                    if (inLayer == 0) {
                        continue;
                    }
                    int offset = y - middle;
                    widest = Math.max(widest, Math.abs(offset));
                    if (Math.abs(offset) <= reach) {
                        byOffset[offset + reach] += inLayer;
                    }
                }
            }
            meanBlocks[size] = round(blocks / (double) ROLLS);
            boxHeight[size] = blocks == 0 ? 0 : round(weightedHeight / blocks);
            for (int i = 0; i < byOffset.length; i++) {
                kernel[size][i] = round(byOffset[i] / (double) ROLLS);
            }
        }
        if (widest > reach) {
            fail("A vein placed a block " + widest + " layers from its box middle; KERNEL_REACH is " + reach);
        }
        boolean same = true;
        for (int size = 1; size <= VeinShapeTables.MAX_SIZE; size++) {
            same &= meanBlocks[size] == VeinShapeTables.MEAN_BLOCKS[size];
            same &= boxHeight[size] == VeinShapeTables.BLOCK_WEIGHTED_BOX_HEIGHT[size];
            same &= Arrays.equals(kernel[size], VeinShapeTables.LAYER_KERNEL[size]);
        }
        same &= VeinShapeTables.LAYER_KERNEL.length == VeinShapeTables.MAX_SIZE + 1;
        if (!same) {
            fail("VeinShapeTables differs from a fresh run. Paste:\nMEAN_BLOCKS = " + format(meanBlocks)
                    + "\nBLOCK_WEIGHTED_BOX_HEIGHT = " + format(boxHeight) + "\nLAYER_KERNEL = " + formatRows(kernel));
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String formatRows(double[][] rows) {
        StringBuilder out = new StringBuilder("{\n");
        for (double[] row : rows) {
            out.append("            {");
            for (int i = 0; i < row.length; i++) {
                out.append(i == 0 ? "" : ", ").append(String.format(Locale.ROOT, "%.3f", row[i]));
            }
            out.append("},\n");
        }
        return out.append("    };").toString();
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
