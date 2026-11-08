package com.orevault.orevault.core.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Vanilla's ore vein shape, ported to pure Java (§3.1; ADR 0009 "One pure record"; ADR 0004 "Placement returns extents";
 * {@code docs/research/vanilla-ore/orefeature.py}). The Vault places veins itself rather than calling vanilla's
 * {@code OreFeature}, which needs a {@code WorldGenLevel}, writes blocks instead of returning them and spills into the
 * next chunk. So the shape is rolled here about an origin of 0,0,0, into solid rock, and the placer decides where it sits.
 *
 * <p>A configured size is not a block count: size 9 places about 6.8 blocks. {@link #meanBlocks} gives the mean blocks a
 * size places and {@link #sizeForMeanBlocks} the inverse, which size bonuses use (ADR 0004 "Size bonuses"). Both read
 * {@link VeinShapeTables}, which a fixed-seed run of this port wrote and a unit test regenerates, rather than running the
 * shape at start-up: ADR 0009 rejects computing the baseline's figures by running the placer at runtime, since it would
 * hide drift between the shape and the curve instead of failing on it.
 *
 * <p>The draw order is vanilla's: one {@code nextFloat} for the angle, two {@code nextInt(3)} for the end heights, then one
 * {@code nextDouble} per sphere. Trigonometry is {@link StrictMath}, so a chunk comes out the same on every JVM (ADR 0003).
 */
public final class VeinShape {
    /** The largest size whose box always fits in a chunk's 16 columns (ADR 0004); a larger request is split. */
    public static final int MAX_IN_CHUNK = 37;

    private static final double PI_F = (float) Math.PI;

    private VeinShape() {
    }

    /**
     * The widest a vein of this size can be after flooring: at most {@code s/8} from its origin along its axis plus a
     * sphere radius of at most {@code s/16 + 1/2}, on both sides (ADR 0004). Rounded up so the bound stays conservative
     * and gives 16 at 37 and 17 at 38 (Balancer, BAL-E4-7: the floored form would allow 39).
     */
    public static int maxHorizontalSpan(int size) {
        return Math.ceilDiv(3 * size, 8) + 2;
    }

    /** Mean blocks a vein of this configured size places in solid rock. */
    public static double meanBlocks(int size) {
        return VeinShapeTables.MEAN_BLOCKS[checkTabled(size)];
    }

    /**
     * Mean box height of a vein of this size, weighted by the blocks each roll placed: E[blocks × h] / E[blocks]. Not the
     * plain mean box height. ADR 0009's edge factor averages {@code H / (H + h - 1)} over blocks, and taller boxes hold more
     * of them; the plain mean understates deepslate's factor (Balancer, BAL-E4-5).
     */
    public static double blockWeightedBoxHeight(int size) {
        return VeinShapeTables.BLOCK_WEIGHTED_BOX_HEIGHT[checkTabled(size)];
    }

    /**
     * The configured size, as a fraction, whose mean blocks is {@code mean}: linear between table rows. The placer draws
     * the two neighbouring sizes with the probabilities that make the mean exact (BAL-E4-10). Sizes 1 and 2 place nothing,
     * so a request at or under size 3's mean is size 3.
     */
    public static double sizeForMeanBlocks(double mean) {
        double[] table = VeinShapeTables.MEAN_BLOCKS;
        if (!(mean > table[3])) {
            return 3;
        }
        for (int size = 4; size <= VeinShapeTables.MAX_SIZE; size++) {
            if (mean <= table[size]) {
                return size - 1 + (mean - table[size - 1]) / (table[size] - table[size - 1]);
            }
        }
        throw new IllegalArgumentException("No tabled size places " + mean + " blocks; split the vein first");
    }

    /** One vein, rolled about the origin. */
    public static Rolled roll(int size, SplittableRandom random) {
        if (size < 1) {
            throw new IllegalArgumentException("Vein size " + size);
        }
        float angle = (float) (random.nextFloat() * PI_F);
        float spread = size / 8.0F;
        int pad = (int) Math.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x0 = StrictMath.sin(angle) * spread;
        double x1 = -StrictMath.sin(angle) * spread;
        double z0 = StrictMath.cos(angle) * spread;
        double z1 = -StrictMath.cos(angle) * spread;
        double y0 = random.nextInt(3) - 2;
        double y1 = random.nextInt(3) - 2;
        int ceilSpread = (int) Math.ceil(spread);
        int floorX = -ceilSpread - pad;
        int floorY = -2 - pad;
        int floorZ = -ceilSpread - pad;

        double[] spheres = new double[size * 4];
        for (int step = 0; step < size; step++) {
            float t = step / (float) size;
            double radiusScale = random.nextDouble() * size / 16.0;
            double radius = ((StrictMath.sin(PI_F * t) + 1.0) * radiusScale + 1.0) / 2.0;
            spheres[step * 4] = x0 + (x1 - x0) * t;
            spheres[step * 4 + 1] = y0 + (y1 - y0) * t;
            spheres[step * 4 + 2] = z0 + (z1 - z0) * t;
            spheres[step * 4 + 3] = radius;
        }
        for (int a = 0; a < size - 1; a++) {
            if (spheres[a * 4 + 3] <= 0) {
                continue;
            }
            for (int b = a + 1; b < size; b++) {
                if (spheres[b * 4 + 3] <= 0) {
                    continue;
                }
                double dx = spheres[a * 4] - spheres[b * 4];
                double dy = spheres[a * 4 + 1] - spheres[b * 4 + 1];
                double dz = spheres[a * 4 + 2] - spheres[b * 4 + 2];
                double dr = spheres[a * 4 + 3] - spheres[b * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0) {
                        spheres[b * 4 + 3] = -1;
                    } else {
                        spheres[a * 4 + 3] = -1;
                    }
                }
            }
        }

        Set<Long> seen = new HashSet<>();
        List<int[]> placed = new ArrayList<>();
        for (int step = 0; step < size; step++) {
            double radius = spheres[step * 4 + 3];
            if (radius < 0) {
                continue;
            }
            double cx = spheres[step * 4];
            double cy = spheres[step * 4 + 1];
            double cz = spheres[step * 4 + 2];
            int minX = Math.max((int) Math.floor(cx - radius), floorX);
            int minY = Math.max((int) Math.floor(cy - radius), floorY);
            int minZ = Math.max((int) Math.floor(cz - radius), floorZ);
            int maxX = Math.max((int) Math.floor(cx + radius), minX);
            int maxY = Math.max((int) Math.floor(cy + radius), minY);
            int maxZ = Math.max((int) Math.floor(cz + radius), minZ);
            for (int x = minX; x <= maxX; x++) {
                double ex = (x + 0.5 - cx) / radius;
                if (ex * ex >= 1.0) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    double ey = (y + 0.5 - cy) / radius;
                    if (ex * ex + ey * ey >= 1.0) {
                        continue;
                    }
                    for (int z = minZ; z <= maxZ; z++) {
                        double ez = (z + 0.5 - cz) / radius;
                        if (ex * ex + ey * ey + ez * ez < 1.0 && seen.add(key(x, y, z))) {
                            placed.add(new int[] {x, y, z});
                        }
                    }
                }
            }
        }
        return Rolled.of(placed);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0xFFFF) << 32) | ((long) (y & 0xFFFF) << 16) | (z & 0xFFFF);
    }

    private static int checkTabled(int size) {
        if (size < 1 || size > VeinShapeTables.MAX_SIZE) {
            throw new IllegalArgumentException("No tabled vein size " + size);
        }
        return size;
    }

    /**
     * A rolled vein: its box about the origin and which of the box's positions it fills. Bit {@code (y * sizeZ + z) *
     * sizeX + x}, relative to the box's minimum corner, is set for a filled position. The array is copied in and out, so
     * the value never changes once built (ADR 0004 "the record's arrays are copied when it is built and never handed out
     * for writing"). An empty vein has a box of zero size; vanilla's rare sizes are empty a few percent of the time.
     */
    public static final class Rolled {
        private static final Rolled EMPTY = new Rolled(0, 0, 0, 0, 0, 0, new long[0]);

        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final long[] bits;
        private final int blocks;

        public Rolled(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, long[] bits) {
            if (sizeX < 0 || sizeY < 0 || sizeZ < 0) {
                throw new IllegalArgumentException("Negative box size");
            }
            int volume = sizeX * sizeY * sizeZ;
            if (bits.length != (volume + 63) / 64) {
                throw new IllegalArgumentException(bits.length + " longs for a box of " + volume);
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.bits = bits.clone();
            int count = 0;
            for (long word : this.bits) {
                count += Long.bitCount(word);
            }
            this.blocks = count;
        }

        static Rolled of(List<int[]> positions) {
            if (positions.isEmpty()) {
                return EMPTY;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int[] p : positions) {
                minX = Math.min(minX, p[0]);
                minY = Math.min(minY, p[1]);
                minZ = Math.min(minZ, p[2]);
                maxX = Math.max(maxX, p[0]);
                maxY = Math.max(maxY, p[1]);
                maxZ = Math.max(maxZ, p[2]);
            }
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            long[] bits = new long[(sizeX * sizeY * sizeZ + 63) / 64];
            for (int[] p : positions) {
                int index = ((p[1] - minY) * sizeZ + (p[2] - minZ)) * sizeX + (p[0] - minX);
                bits[index >>> 6] |= 1L << (index & 63);
            }
            return new Rolled(minX, minY, minZ, sizeX, sizeY, sizeZ, bits);
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

        /** How many positions the vein fills. */
        public int blocks() {
            return blocks;
        }

        /** Whether the box position {@code x, y, z}, relative to the box's minimum corner, is filled. */
        public boolean isSet(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                return false;
            }
            int index = (y * sizeZ + z) * sizeX + x;
            return (bits[index >>> 6] & (1L << (index & 63))) != 0;
        }

        /** A copy of the packed bits. */
        public long[] bits() {
            return bits.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Rolled that
                    && minX == that.minX && minY == that.minY && minZ == that.minZ
                    && sizeX == that.sizeX && sizeY == that.sizeY && sizeZ == that.sizeZ
                    && Arrays.equals(bits, that.bits);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * (31 * (31 * (31 * (31 * minX + minY) + minZ) + sizeX) + sizeY) + sizeZ) + Arrays.hashCode(bits);
        }

        @Override
        public String toString() {
            return "Rolled[" + blocks + " blocks in " + sizeX + "x" + sizeY + "x" + sizeZ + " at " + minX + "," + minY + "," + minZ + "]";
        }
    }
}
