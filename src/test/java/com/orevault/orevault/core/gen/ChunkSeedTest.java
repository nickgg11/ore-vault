package com.orevault.orevault.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The seed is save data in effect: changing it changes every Vault that has yet to generate a chunk (ADR 0003). The
 * golden values were worked out apart from the Java code, by a script of the same function.
 */
class ChunkSeedTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");

    @Test
    void theGoldenSeedsDoNotMove() {
        assertEquals(-0x1EE7F8D248935EF7L, ChunkSeed.of(TEAM, 0, 0, 0, 0L));
        assertEquals(-0x3B876A26FD3AF040L, ChunkSeed.of(TEAM, 3, -7, 12, 1L));
    }

    @Test
    void eachHalfOfTheTeamIdCounts() {
        long seed = ChunkSeed.of(new UUID(1L, 2L), 0, 0, 0, 0L);
        assertNotEquals(seed, ChunkSeed.of(new UUID(3L, 2L), 0, 0, 0, 0L));
        assertNotEquals(seed, ChunkSeed.of(new UUID(1L, 3L), 0, 0, 0, 0L));
    }

    @Test
    void aResetMovesTheSeed() {
        assertNotEquals(ChunkSeed.of(TEAM, 0, 4, 5, 0L), ChunkSeed.of(TEAM, 1, 4, 5, 0L));
    }

    @Test
    void xAndZAreNotInterchangeable() {
        assertNotEquals(ChunkSeed.of(TEAM, 0, 4, 5, 0L), ChunkSeed.of(TEAM, 0, 5, 4, 0L));
    }

    @Test
    void aNegativeChunkIsNotItsPositiveTwin() {
        assertNotEquals(ChunkSeed.of(TEAM, 0, -4, 5, 0L), ChunkSeed.of(TEAM, 0, 4, 5, 0L));
    }

    /** Each feature has a stream of its own, so adding one never shifts another's placement (§3.1). */
    @Test
    void aSaltMovesTheSeed() {
        assertNotEquals(ChunkSeed.of(TEAM, 0, 4, 5, 1L), ChunkSeed.of(TEAM, 0, 4, 5, 2L));
    }

    @Test
    void theSameInputsGiveTheSameSeed() {
        assertEquals(ChunkSeed.of(TEAM, 2, 9, 9, 7L), ChunkSeed.of(TEAM, 2, 9, 9, 7L));
    }
}
