package com.orevault.orevault.core.gen;

/**
 * Each generation feature's salt for {@link ChunkSeed#of} (ADR 0003 "Seeds and per-chunk rolls"): every feature draws
 * from its own stream, so adding a feature, or a node that adds veins, shifts no other feature's placement. Save data in
 * effect: changing one regenerates every Vault chunk differently, so a golden test pins them.
 */
public final class GenSalts {
    /** The baseline ore veins of both bands, stone first (§3.1). */
    public static final long BASELINE_ORE = 0x4F52455F42415345L;
    /** Gravel blobs, placed after ore (§3.1). */
    public static final long GRAVEL = 0x475241564C5F5F31L;
    /** Clay blobs, placed after gravel (§3.1). */
    public static final long CLAY = 0x434C41595F5F5F31L;

    private GenSalts() {
    }
}
