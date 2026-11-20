package com.orevault.orevault.core.ore;

import java.nio.charset.StandardCharsets;

/**
 * The seed the classifier samples one placed feature's count and height providers from (ADR 0009 step 6): FNV-1a 64 over
 * the feature id's UTF-8 bytes. A fixed function of the id, not of the order features are met in nor of
 * {@code String#hashCode}'s 32 bits, so every server start samples a feature the same and adding a feature never moves
 * another's draws (Epic 4 triage, DEV-E4-5 and RV-E4-6). The identity pick is then stable across restarts, which a chunk
 * generated after one needs (ADR 0003).
 */
public final class SamplingSeed {
    private static final long OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long PRIME = 0x100000001b3L;

    private SamplingSeed() {
    }

    public static long of(String featureId) {
        long hash = OFFSET_BASIS;
        for (byte b : featureId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= PRIME;
        }
        return hash;
    }
}
