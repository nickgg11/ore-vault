package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The port of vanilla's vein shape (ADR 0004, ADR 0009; {@code docs/research/vanilla-ore-generation.md} section 2). */
class VeinShapeTest {
    private static final int ROLLS = 10_000;

    /** The research's Python port is the oracle. Size 3 is noisy (BAL-E4-9), so it gets 6%; 9 and 17 get 1%. */
    @Test
    void meanBlocksMatchTheResearchOracle() {
        assertWithin(0.46, rolledMean(3, 100_000), 0.06);
        assertWithin(6.79, rolledMean(9, ROLLS), 0.01);
        assertWithin(22.15, rolledMean(17, ROLLS), 0.01);
    }

    @Test
    void theTableAgreesWithTheOracleToo() {
        assertWithin(0.46, VeinShape.meanBlocks(3), 0.06);
        assertWithin(6.79, VeinShape.meanBlocks(9), 0.01);
        assertWithin(22.15, VeinShape.meanBlocks(17), 0.01);
    }

    /** ADR 0004: the formula gives 16 at 37 and 17 at 38, so 37 is the largest size that always fits a chunk. */
    @Test
    void theSpanFormulaPutsTheLimitAt37() {
        assertEquals(16, VeinShape.maxHorizontalSpan(VeinShape.MAX_IN_CHUNK));
        assertEquals(17, VeinShape.maxHorizontalSpan(VeinShape.MAX_IN_CHUNK + 1));
        for (int size = 1; size <= VeinShape.MAX_IN_CHUNK; size++) {
            assertTrue(VeinShape.maxHorizontalSpan(size) <= 16, "size " + size);
        }
    }

    /** ADR 0004: 10,000 rolls at 37 from a fixed seed, every box inside 16 columns and inside the formula. */
    @Test
    void everyBoxAt37FitsAChunk() {
        SplittableRandom random = GenRandom.of(37L);
        int limit = VeinShape.maxHorizontalSpan(VeinShape.MAX_IN_CHUNK);
        for (int i = 0; i < ROLLS; i++) {
            VeinShape.Rolled vein = VeinShape.roll(VeinShape.MAX_IN_CHUNK, random);
            assertTrue(vein.sizeX() <= limit && vein.sizeZ() <= limit, vein.toString());
        }
    }

    @Test
    void theSameSeedRollsTheSameVein() {
        assertEquals(VeinShape.roll(12, GenRandom.of(99L)), VeinShape.roll(12, GenRandom.of(99L)));
    }

    @Test
    void theBlockCountMatchesTheBits() {
        SplittableRandom random = GenRandom.of(5L);
        for (int i = 0; i < 1_000; i++) {
            VeinShape.Rolled vein = VeinShape.roll(10, random);
            int counted = 0;
            for (int x = 0; x < vein.sizeX(); x++) {
                for (int y = 0; y < vein.sizeY(); y++) {
                    for (int z = 0; z < vein.sizeZ(); z++) {
                        counted += vein.isSet(x, y, z) ? 1 : 0;
                    }
                }
            }
            assertEquals(vein.blocks(), counted);
        }
    }

    /** ARCH-E4-10: the bits are copied out, so a caller cannot change a rolled vein. */
    @Test
    void theBitsCannotBeChangedFromOutside() {
        VeinShape.Rolled vein = VeinShape.roll(12, GenRandom.of(3L));
        long[] bits = vein.bits();
        assertNotSame(bits, vein.bits());
        java.util.Arrays.fill(bits, 0L);
        assertEquals(VeinShape.roll(12, GenRandom.of(3L)), vein);
    }

    @Test
    void positionsOutsideTheBoxAreEmpty() {
        VeinShape.Rolled vein = VeinShape.roll(9, GenRandom.of(1L));
        assertFalse(vein.isSet(-1, 0, 0));
        assertFalse(vein.isSet(vein.sizeX(), 0, 0));
    }

    /** BAL-E4-10: the inverse lands on the table's rows, interpolates between them, and clamps at size 3. */
    @Test
    void theInverseFindsTheSize() {
        assertEquals(9.0, VeinShape.sizeForMeanBlocks(VeinShape.meanBlocks(9)), 1e-9);
        double halfway = (VeinShape.meanBlocks(14) + VeinShape.meanBlocks(15)) / 2;
        assertEquals(14.5, VeinShape.sizeForMeanBlocks(halfway), 1e-9);
        assertEquals(3.0, VeinShape.sizeForMeanBlocks(0.1));
        assertThrows(IllegalArgumentException.class, () -> VeinShape.sizeForMeanBlocks(1e6));
    }

    @Test
    void theTableRisesStrictlyFromSize3() {
        for (int size = 4; size <= VeinShapeTables.MAX_SIZE; size++) {
            assertTrue(VeinShape.meanBlocks(size) > VeinShape.meanBlocks(size - 1), "size " + size);
        }
    }

    private static double rolledMean(int size, int rolls) {
        SplittableRandom random = GenRandom.of(size * 1_000_003L);
        long blocks = 0;
        for (int i = 0; i < rolls; i++) {
            blocks += VeinShape.roll(size, random).blocks();
        }
        return blocks / (double) rolls;
    }

    private static void assertWithin(double expected, double actual, double relative) {
        assertTrue(Math.abs(actual - expected) <= expected * relative,
                "expected " + expected + " within " + (relative * 100) + "%, got " + actual);
    }
}
