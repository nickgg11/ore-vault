package com.orevault.orevault.core.gen;

import java.util.SplittableRandom;

/**
 * The one random algorithm every Vault generation feature uses (ADR 0003 "Seeds and per-chunk rolls"). A chunk generated
 * next year has to come out the same as one generated today from the same seed, so the algorithm is named, not taken
 * from {@code RandomGenerator.getDefault()}, which a JDK release may change. {@link SplittableRandom}'s algorithm is
 * specified by the JDK. Like {@link ChunkSeed}, this is save data in effect, and a golden test pins its first draws.
 *
 * <p>One instance per feature per chunk, made from that feature's seed and never shared between threads.
 */
public final class GenRandom {
    private GenRandom() {
    }

    /** A fresh random for one feature in one chunk, from {@link ChunkSeed#of}. */
    public static SplittableRandom of(long seed) {
        return new SplittableRandom(seed);
    }
}
