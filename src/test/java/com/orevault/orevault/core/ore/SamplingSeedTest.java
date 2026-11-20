package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * The classifier's per-feature seed is a fixed function of the feature id (Epic 4 triage, DEV-E4-5 and RV-E4-6), so a
 * restart, another JVM or another order of biomes samples every feature the same. Golden values are FNV-1a 64 over the
 * id's UTF-8 bytes, computed outside Java.
 */
class SamplingSeedTest {
    @Test
    void matchesFnv1a64() {
        assertEquals(-3750763034362895579L, SamplingSeed.of(""));
        assertEquals(-5808556873153909620L, SamplingSeed.of("a"));
        assertEquals(5412432509347637389L, SamplingSeed.of("minecraft:ore_diamond"));
        assertEquals(1007756796908252363L, SamplingSeed.of("minecraft:ore_coal_upper"));
    }

    @Test
    void twoFeaturesGetTwoSeeds() {
        assertNotEquals(SamplingSeed.of("minecraft:ore_diamond"), SamplingSeed.of("minecraft:ore_diamond_large"));
    }
}
