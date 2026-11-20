package com.orevault.orevault.core.gen;

import java.util.Arrays;
import java.util.Objects;

/**
 * One placed vein, as a value (ADR 0004 "Placement returns extents"; S20): its box in chunk-local x and z and absolute y,
 * cut to its band, which of the box's positions it holds, its ore, its ordinal and whether it is a guide vein. The
 * generator writes blocks from these values and the vein index (E9-S4) is encoded from the same ones, so the world and
 * the index agree at generation. Bit {@code (y * sizeZ + z) * sizeX + x}, relative to the box's minimum corner, is set
 * for a position the vein holds. The array is copied in and out (ADR 0004; Architect, ARCH-E4-10).
 */
public final class VeinExtent {
    private final int ordinal;
    private final String oreGroup;
    private final Band band;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final long[] bits;
    private final boolean guide;
    private final int blocks;

    public VeinExtent(int ordinal, String oreGroup, Band band, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
            long[] bits, boolean guide) {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || minX < 0 || minZ < 0 || minX + sizeX > 16 || minZ + sizeZ > 16) {
            throw new IllegalArgumentException("A box of " + sizeX + "x" + sizeY + "x" + sizeZ + " at " + minX + "," + minZ);
        }
        if (bits.length != (sizeX * sizeY * sizeZ + 63) / 64) {
            throw new IllegalArgumentException(bits.length + " longs for the box");
        }
        this.ordinal = ordinal;
        this.oreGroup = Objects.requireNonNull(oreGroup);
        this.band = Objects.requireNonNull(band);
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.bits = bits.clone();
        this.guide = guide;
        int count = 0;
        for (long word : this.bits) {
            count += Long.bitCount(word);
        }
        this.blocks = count;
    }

    public int ordinal() {
        return ordinal;
    }

    public String oreGroup() {
        return oreGroup;
    }

    public Band band() {
        return band;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public boolean guide() {
        return guide;
    }

    /** How many positions the vein holds after later veins took theirs. */
    public int blocks() {
        return blocks;
    }

    /** The box's volume in bits, what the index stores for it. */
    public int boxVolume() {
        return sizeX * sizeY * sizeZ;
    }

    /** Whether box position {@code x, y, z}, relative to the box's minimum corner, is held by this vein. */
    public boolean isSet(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return false;
        }
        int index = (y * sizeZ + z) * sizeX + x;
        return (bits[index >>> 6] & (1L << (index & 63))) != 0;
    }

    public long[] bits() {
        return bits.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VeinExtent that
                && ordinal == that.ordinal && guide == that.guide && band == that.band && oreGroup.equals(that.oreGroup)
                && minX == that.minX && minY == that.minY && minZ == that.minZ
                && sizeX == that.sizeX && sizeY == that.sizeY && sizeZ == that.sizeZ
                && Arrays.equals(bits, that.bits);
    }

    @Override
    public int hashCode() {
        int hash = Objects.hash(ordinal, oreGroup, band.ordinal(), minX, minY, minZ, sizeX, sizeY, sizeZ, guide);
        return 31 * hash + Arrays.hashCode(bits);
    }

    @Override
    public String toString() {
        return "VeinExtent[#" + ordinal + " " + oreGroup + " " + band + ", " + blocks + " blocks in " + sizeX + "x" + sizeY + "x"
                + sizeZ + " at " + minX + "," + minY + "," + minZ + (guide ? ", guide" : "") + "]";
    }
}
