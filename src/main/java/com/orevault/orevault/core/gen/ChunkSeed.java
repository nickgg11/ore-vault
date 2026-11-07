package com.orevault.orevault.core.gen;

import java.util.UUID;

/**
 * The one seed function for a Vault chunk (§3.1, ADR 0003): both halves of the team id, the reset count, the chunk and a
 * per-feature salt, and never the world seed. A Vault's chunks generate over months, so a chunk has to come out the same
 * whenever it is first visited, and a reset has to give fresh veins. The mix is SplitMix64's. It is save data in effect:
 * the golden test makes a change to it a reviewed decision.
 */
public final class ChunkSeed {
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private ChunkSeed() {
    }

    public static long of(UUID teamId, int vaultGeneration, int chunkX, int chunkZ, long salt) {
        long state = absorb(0L, teamId.getMostSignificantBits());
        state = absorb(state, teamId.getLeastSignificantBits());
        state = absorb(state, vaultGeneration);
        state = absorb(state, chunkX);
        state = absorb(state, chunkZ);
        return absorb(state, salt);
    }

    private static long absorb(long state, long value) {
        return mix((state + GAMMA) ^ value);
    }

    private static long mix(long value) {
        long z = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
