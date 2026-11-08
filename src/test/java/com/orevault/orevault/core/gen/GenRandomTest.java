package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** ADR 0003: the algorithm is save data in effect, so its first draws are pinned (Architect, ARCH-E4-6). */
class GenRandomTest {
    /** SplittableRandom's specified SplitMix64 stream from seed 42; a JDK that changed it would fail here first. */
    @Test
    void theFirstDrawsArePinned() {
        SplittableRandom random = GenRandom.of(42L);
        assertEquals(-4767286540954276203L, random.nextLong());
        assertEquals(2949826092126892291L, random.nextLong());
        assertEquals(5139283748462763858L, random.nextLong());
    }

    @Test
    void theSameSeedGivesTheSameStream() {
        SplittableRandom a = GenRandom.of(7L);
        SplittableRandom b = GenRandom.of(7L);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextDouble(), b.nextDouble());
        }
    }
}
