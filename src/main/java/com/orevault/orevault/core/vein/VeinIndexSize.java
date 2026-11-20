package com.orevault.orevault.core.vein;

import com.orevault.orevault.core.gen.VeinExtent;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The bytes a chunk's vein index costs under ADR 0004's encoding, for the size gate measured before E4-S3 (ADR 0004
 * "Pending measurement"): one 8-byte header a vein, the standing bitsets of every vein concatenated into one
 * {@code long[]} (not padded per vein; Balancer, BAL-E4-2), and the palette's distinct ore ids as UTF-8. No NBT overhead
 * and no chunk fields. The encoder (E9-S4) asserts it writes exactly this many bytes for the same extents, so this stays a
 * measurement of the real encoding.
 */
public final class VeinIndexSize {
    /** The comparator: the chunk's block data at 6 bits a block, 98,304 × 6 / 8 (ADR 0004). */
    public static final int COMPARATOR_BYTES = 73_728;

    private static final int HEADER_BYTES = 8;

    private VeinIndexSize() {
    }

    public static long bytes(List<VeinExtent> veins) {
        long boxBits = 0;
        Set<String> palette = new LinkedHashSet<>();
        for (VeinExtent vein : veins) {
            boxBits += vein.boxVolume();
            palette.add(vein.oreGroup());
        }
        return bytes(veins.size(), boxBits, palette);
    }

    /** The same arithmetic from counts, for a measurement that grows the boxes (the gate's Stone Reduction bound). */
    public static long bytes(int veins, long totalBoxBits, Set<String> palette) {
        long paletteBytes = 0;
        for (String id : palette) {
            paletteBytes += id.getBytes(StandardCharsets.UTF_8).length;
        }
        return (long) HEADER_BYTES * veins + Math.ceilDiv(totalBoxBits, 64L) * 8 + paletteBytes;
    }
}
