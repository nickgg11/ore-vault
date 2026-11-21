package com.orevault.orevault.core.gen;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Rolls a chunk's gravel and clay blobs (§3.1 "Gravel and clay"; ADR 0004 "Placement returns extents"; Architect,
 * ARCH-E4-8). Pure, like {@link VeinPlacer}: it reads no world and no team state, and returns the positions the generator
 * writes, so "gravel never replaces ore" is a unit test rather than a branch in the generator.
 *
 * <p>Each kind draws from its own stream, seeded with its own salt ({@link GenSalts#GRAVEL}, {@link GenSalts#CLAY}), so a
 * change to the ore placement or to clay never moves a gravel blob, only which of its positions it keeps. Gravel is
 * placed after ore and clay after gravel; a blob takes only positions that are still the band's filler, so it never
 * replaces ore and clay never replaces gravel. Each blob is the vanilla vein shape at the band's configured size, with its
 * box inside the chunk's 16 by 16 columns, and its lowest layer drawn like a vein's: from the {@code H + h - 1} positions
 * that leave a layer inside the band, keeping only the positions inside it. Blobs are not clipped and are not raised by an
 * edge factor; they count as stone for the 40% floor (§3.1).
 *
 * <p>The draw sequence, per kind: per band the variant has, stone first, one count draw (floor plus a fractional draw, as
 * the placer's), then per blob the shape (vanilla's own draws), origin x, origin z and the lowest layer. An empty blob
 * still takes all its draws.
 */
public final class BlobPlacer {
    private BlobPlacer() {
    }

    /**
     * What the placer returns: chunk cells, each {@link #cell}, sorted ascending.
     *
     * @param gravel the cells that become gravel
     * @param clay the cells that become clay
     */
    public record Placement(int[] gravel, int[] clay) {
        public static final Placement NONE = new Placement(new int[0], new int[0]);

        public Placement {
            gravel = gravel.clone();
            clay = clay.clone();
        }

        @Override
        public int[] gravel() {
            return gravel.clone();
        }

        @Override
        public int[] clay() {
            return clay.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Placement that && Arrays.equals(gravel, that.gravel) && Arrays.equals(clay, that.clay);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(gravel) + Arrays.hashCode(clay);
        }

        @Override
        public String toString() {
            return "BlobPlacer.Placement[" + gravel.length + " gravel, " + clay.length + " clay]";
        }
    }

    /** A chunk-local position as one int: {@code ((y - minY) * 16 + z) * 16 + x}, the placer's own cell order. */
    public static int cell(VaultVariant variant, int x, int y, int z) {
        return ((y - variant.minY()) * 16 + z) * 16 + x;
    }

    public static int x(int cell) {
        return cell & 15;
    }

    public static int z(int cell) {
        return (cell >>> 4) & 15;
    }

    public static int y(VaultVariant variant, int cell) {
        return variant.minY() + (cell >>> 8);
    }

    /**
     * The blobs for a chunk.
     *
     * @param ore the chunk's ore veins, whose positions no blob takes
     * @param gravelSeed the chunk's seed for {@link GenSalts#GRAVEL}
     * @param claySeed the chunk's seed for {@link GenSalts#CLAY}
     */
    public static Placement place(OreBaseline baseline, VaultVariant variant, List<VeinExtent> ore, long gravelSeed, long claySeed) {
        BitSet taken = new BitSet(256 * variant.height());
        for (VeinExtent vein : ore) {
            for (int y = 0; y < vein.sizeY(); y++) {
                for (int z = 0; z < vein.sizeZ(); z++) {
                    for (int x = 0; x < vein.sizeX(); x++) {
                        if (vein.isSet(x, y, z)) {
                            taken.set(cell(variant, vein.minX() + x, vein.minY() + y, vein.minZ() + z));
                        }
                    }
                }
            }
        }
        int[] gravel = roll(baseline.gravel(), variant, taken, GenRandom.of(gravelSeed));
        int[] clay = roll(baseline.clay(), variant, taken, GenRandom.of(claySeed));
        return new Placement(gravel, clay);
    }

    /** One kind's blobs through every band, marking each position it takes in {@code taken}. */
    private static int[] roll(Map<Band, OreBaseline.Blobs> blobs, VaultVariant variant, BitSet taken, SplittableRandom random) {
        BitSet mine = new BitSet(256 * variant.height());
        for (Band band : Band.values()) {
            BandBounds bounds = variant.band(band).orElse(null);
            if (bounds == null) {
                continue;
            }
            OreBaseline.Blobs kind = blobs.get(band);
            if (kind.size() > VeinShape.MAX_IN_CHUNK) {
                throw new IllegalArgumentException("Blob size " + kind.size() + " does not fit in a chunk (ADR 0004)");
            }
            int count = VeinPlacer.rollCount(kind.perChunk(), random);
            for (int b = 0; b < count; b++) {
                VeinShape.Rolled shape = VeinShape.roll(kind.size(), random);
                int sizeX = Math.max(1, shape.sizeX());
                int sizeY = Math.max(1, shape.sizeY());
                int sizeZ = Math.max(1, shape.sizeZ());
                int x0 = random.nextInt(16 - sizeX + 1);
                int z0 = random.nextInt(16 - sizeZ + 1);
                int y0 = bounds.bottomY() - (sizeY - 1) + random.nextInt(bounds.layers() + sizeY - 1);
                if (shape.blocks() == 0) {
                    continue;
                }
                for (int by = 0; by < sizeY; by++) {
                    int y = y0 + by;
                    if (y < bounds.bottomY() || y > bounds.topY()) {
                        continue;
                    }
                    for (int bz = 0; bz < sizeZ; bz++) {
                        for (int bx = 0; bx < sizeX; bx++) {
                            if (!shape.isSet(bx, by, bz)) {
                                continue;
                            }
                            int cell = cell(variant, x0 + bx, y, z0 + bz);
                            if (!taken.get(cell)) {
                                taken.set(cell);
                                mine.set(cell);
                            }
                        }
                    }
                }
            }
        }
        return mine.stream().toArray();
    }
}
