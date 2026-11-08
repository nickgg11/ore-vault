package com.orevault.orevault.core.gen;

/**
 * Per-size figures of {@link VeinShape}, indexed by configured size (index 0 unused), for sizes 1 to {@link #MAX_SIZE}:
 * the Vault's largest baseline size is 33 (gravel and clay), and veins above {@link VeinShape#MAX_IN_CHUNK} are split.
 *
 * <p>Written by a fixed-seed run of the port, {@code VeinShapeTablesTest#theTablesMatchAFreshRun}, which regenerates them
 * with {@link GenRandom} and fails, printing this file's arrays, if they differ. They are not computed at start-up (ADR
 * 0009 rejects figures computed by running the placer at runtime). A change here moves the level curve with it, which is
 * the point: the curve and the generator read one source.
 */
final class VeinShapeTables {
    static final int MAX_SIZE = 40;

    /** Mean blocks a vein places in solid rock. */
    static final double[] MEAN_BLOCKS = {
            0.000, 0.000, 0.000, 0.450, 1.999, 3.135, 4.054, 4.969,
            5.677, 6.798, 8.387, 10.035, 11.632, 13.399, 15.214, 17.262,
            19.565, 22.192, 25.318, 28.935, 32.851, 36.801, 40.981, 45.690,
            50.767, 55.862, 61.367, 67.220, 73.843, 80.543, 87.846, 95.633,
            103.781, 112.160, 121.477, 130.975, 141.550, 152.540, 163.671, 175.287,
            187.638,
    };

    /** E[blocks × box height] / E[blocks]. */
    static final double[] BLOCK_WEIGHTED_BOX_HEIGHT = {
            0.000, 0.000, 0.000, 1.104, 1.745, 1.906, 2.039, 2.140,
            2.239, 2.387, 2.508, 2.645, 2.745, 2.833, 2.866, 2.891,
            2.901, 2.924, 3.051, 3.233, 3.369, 3.468, 3.518, 3.550,
            3.631, 3.798, 3.992, 4.174, 4.302, 4.381, 4.497, 4.621,
            4.727, 4.813, 4.983, 5.164, 5.294, 5.397, 5.473, 5.509,
            5.553,
    };

    private VeinShapeTables() {
    }
}
