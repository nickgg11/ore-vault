package com.orevault.orevault.core.gen;

/**
 * Per-size figures of {@link VeinShape}, indexed by configured size (index 0 unused), for sizes 1 to {@link #MAX_SIZE}:
 * the Vault's largest baseline size is 33 (gravel and clay), and veins above {@link VeinShape#MAX_IN_CHUNK} are split.
 *
 * <p>Written by a fixed-seed run of the port, {@code VeinShapeTablesTest#theTablesMatchAFreshRun}, which regenerates them
 * with {@link GenRandom} and fails, printing this file's arrays, if they differ. They are not computed at start-up (ADR
 * 0009 rejects figures computed by running the placer at runtime). A change here moves the level curve with it, which is
 * the point: the curve and the generator read one source.
 *
 * <p>PROVISIONAL until that test has run once on Nick's PC: the values below come from the Python port
 * ({@code docs/research/vanilla-ore/orefeature.py}, 4,000 rolls a size), so the regeneration test fails until they are
 * replaced with its printout.
 */
final class VeinShapeTables {
    static final int MAX_SIZE = 40;

    /** Mean blocks a vein places in solid rock. */
    static final double[] MEAN_BLOCKS = {
            0.0, 0.000, 0.000, 0.462, 1.988, 3.135, 4.065, 4.954,
            5.685, 6.786, 8.402, 10.087, 11.591, 13.416, 15.272, 17.245,
            19.565, 22.164, 25.350, 29.003, 32.864, 36.747, 40.945, 45.671,
            50.603, 55.977, 61.380, 67.191, 73.650, 80.427, 87.939, 95.800,
            103.591, 112.127, 121.030, 131.064, 141.768, 152.638, 164.049, 175.199,
            187.038,
    };

    /** E[blocks × box height] / E[blocks]. */
    static final double[] BLOCK_WEIGHTED_BOX_HEIGHT = {
            0.0, 0.000, 0.000, 1.100, 1.754, 1.907, 2.016, 2.146,
            2.224, 2.386, 2.503, 2.638, 2.740, 2.820, 2.873, 2.876,
            2.903, 2.921, 3.054, 3.236, 3.366, 3.477, 3.515, 3.546,
            3.605, 3.806, 3.989, 4.176, 4.319, 4.382, 4.491, 4.614,
            4.730, 4.810, 4.996, 5.148, 5.297, 5.396, 5.459, 5.511,
            5.548,
    };

    private VeinShapeTables() {
    }
}
