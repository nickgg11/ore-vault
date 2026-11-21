package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Rolls a chunk's ore veins and returns them as values (ADR 0004 "Placement returns extents"; ADR 0009; S13, S20). The
 * placer only rolls veins by count and size; nothing fills toward a target. It reads no world and no team state, so the
 * same seed gives the same veins whenever, and in whatever order, chunks are generated.
 *
 * <p>The draw sequence is fixed, whatever the pack or the counts (Architect, ARCH-E4-7), and a golden test pins it:
 * requests in the order given (the baseline's: stone then deepslate, each common, uncommon, rare); per request one
 * fraction draw, always; per vein, in this order, the size index, the size fraction, the shape (vanilla's own draws),
 * the box origin x, origin z, the lowest layer, then the ore pick, which takes exactly one {@code nextDouble}. So a
 * pack with forty ores moves which ore a vein is but never where any vein is. An empty vein (vanilla's rare sizes are
 * empty a few percent of the time) still takes all its draws.
 *
 * <p>Where a vein sits. Its box fits in the chunk's 16 by 16 columns, so it never crosses a border (S20). Vertically
 * the box may overhang its band's floor or top: for a box {@code h} layers tall over a region of {@code H} layers, its
 * lowest layer is drawn from the {@code H + h - 1} positions that leave a layer inside, and only positions inside the
 * band are kept, so every layer of the band is covered alike (ADR 0009 "Height", BAL-15). The counts are raised by the
 * edge factor to make up the cut blocks. The vein's layer, which picks its ore, is the rolled box's middle layer,
 * {@code minY + (sizeY - 1) / 2} before it is cut, clamped into the band (BAL-E4-3).
 *
 * <p>Veins are placed in ordinal order, and a later vein that takes an earlier vein's position clears the earlier bit,
 * so the extents match the world exactly and the generator writes every bit of every extent (ADR 0004; ARCH-E4-8). A
 * vein left with no position is dropped.
 *
 * <p>The hard stop is a safety net only: it counts requested blocks, before overwrites, against 60% of the band, and
 * refuses any vein past it (BAL-E4-13). The clip factors are what really hold the floor.
 */
public final class VeinPlacer {
    private VeinPlacer() {
    }

    /**
     * What the placer returns.
     *
     * @param veins every placed vein, in ordinal order
     * @param hardStopped whether the safety net refused a vein in this chunk
     */
    public record Placement(List<VeinExtent> veins, boolean hardStopped) {
        public Placement {
            veins = List.copyOf(veins);
        }
    }

    /** The baseline's veins for a chunk, from the seed of {@link GenSalts#BASELINE_ORE}. */
    public static Placement placeOre(OreBaseline baseline, VaultVariant variant, Map<Band, OrePicker> pickers, ClipFactors clip, long seed) {
        return place(baselineRequests(baseline, variant, clip), variant, pickers, seed);
    }

    /**
     * One request per band the variant has and per rarity, in that order: the count times the rarity's edge factor in the
     * band times the band's clip factor (ADR 0009), spread through the whole band.
     */
    public static List<VeinRequest> baselineRequests(OreBaseline baseline, VaultVariant variant, ClipFactors clip) {
        List<VeinRequest> requests = new ArrayList<>();
        for (Band band : Band.values()) {
            variant.band(band).ifPresent(bounds -> {
                for (Rarity rarity : Rarity.values()) {
                    OreBaseline.Veins veins = baseline.veins(band, rarity);
                    double count = veins.perChunk() * baseline.edgeFactor(band, rarity, variant) * clip.of(band);
                    double[] sizes = new double[veins.maxSize() - veins.minSize() + 1];
                    for (int i = 0; i < sizes.length; i++) {
                        sizes[i] = veins.minSize() + i;
                    }
                    requests.add(new VeinRequest(band, rarity, count, sizes, bounds.bottomY(), bounds.topY(), false));
                }
            });
        }
        return requests;
    }

    /** Rolls every request in order from one seed. */
    public static Placement place(List<VeinRequest> requests, VaultVariant variant, Map<Band, OrePicker> pickers, long seed) {
        SplittableRandom random = GenRandom.of(seed);
        int minY = variant.minY();
        int[] owner = new int[256 * variant.height()];
        List<Builder> placed = new ArrayList<>();
        Map<Band, Long> requested = new EnumMap<>(Band.class);
        boolean hardStopped = false;

        for (VeinRequest request : requests) {
            BandBounds bounds = variant.band(request.band())
                    .orElseThrow(() -> new IllegalArgumentException(variant.id() + " has no " + request.band() + " band"));
            OrePicker picker = pickers.get(request.band());
            if (picker == null) {
                throw new IllegalArgumentException("No ore picker for the " + request.band() + " band");
            }
            if (request.regionBottomY() < bounds.bottomY() || request.regionTopY() > bounds.topY()) {
                throw new IllegalArgumentException("A region outside the " + request.band() + " band");
            }
            long ceiling = (long) Math.floor(ClipFactors.CEILING * 256 * bounds.layers());
            int count = rollCount(request.perChunk(), random);
            int regionLayers = request.regionTopY() - request.regionBottomY() + 1;
            for (int v = 0; v < count; v++) {
                int size = drawSize(request, random);
                VeinShape.Rolled shape = VeinShape.roll(size, random);
                int sizeX = Math.max(1, shape.sizeX());
                int sizeY = Math.max(1, shape.sizeY());
                int sizeZ = Math.max(1, shape.sizeZ());
                int x0 = random.nextInt(16 - sizeX + 1);
                int z0 = random.nextInt(16 - sizeZ + 1);
                int y0 = request.regionBottomY() - (sizeY - 1) + random.nextInt(regionLayers + sizeY - 1);
                double pickDraw = random.nextDouble();
                if (shape.blocks() == 0) {
                    continue;
                }
                int layer = Math.max(bounds.bottomY(), Math.min(bounds.topY(), y0 + (sizeY - 1) / 2));
                int cutBottom = Math.max(y0, bounds.bottomY());
                int cutTop = Math.min(y0 + sizeY - 1, bounds.topY());
                int inside = 0;
                for (int by = cutBottom - y0; by <= cutTop - y0; by++) {
                    for (int bz = 0; bz < sizeZ; bz++) {
                        for (int bx = 0; bx < sizeX; bx++) {
                            inside += shape.isSet(bx, by, bz) ? 1 : 0;
                        }
                    }
                }
                if (inside == 0) {
                    continue;
                }
                long soFar = requested.getOrDefault(request.band(), 0L);
                if (soFar + inside > ceiling) {
                    hardStopped = true;
                    continue;
                }
                requested.put(request.band(), soFar + inside);

                String ore = picker.pick(request.rarity(), layer, pickDraw);
                Builder vein = new Builder(placed.size(), ore, request.band(), x0, cutBottom, z0, sizeX, cutTop - cutBottom + 1, sizeZ, request.guide());
                int self = placed.size() + 1;
                placed.add(vein);
                for (int by = cutBottom - y0; by <= cutTop - y0; by++) {
                    for (int bz = 0; bz < sizeZ; bz++) {
                        for (int bx = 0; bx < sizeX; bx++) {
                            if (!shape.isSet(bx, by, bz)) {
                                continue;
                            }
                            int y = y0 + by;
                            int cell = ((y - minY) * 16 + (z0 + bz)) * 16 + (x0 + bx);
                            int previous = owner[cell];
                            if (previous != 0) {
                                Builder earlier = placed.get(previous - 1);
                                earlier.clear(x0 + bx - earlier.minX, y - earlier.minY, z0 + bz - earlier.minZ);
                            }
                            owner[cell] = self;
                            vein.set(bx, y - cutBottom, bz);
                        }
                    }
                }
            }
        }

        List<VeinExtent> veins = new ArrayList<>(placed.size());
        for (Builder vein : placed) {
            if (vein.blocks > 0) {
                veins.add(vein.build());
            }
        }
        return new Placement(veins, hardStopped);
    }

    /** {@code floor(c)} veins, plus one when a draw falls under the fraction (ADR 0009). Always takes one draw. */
    static int rollCount(double mean, SplittableRandom random) {
        double whole = Math.floor(mean);
        return (int) whole + (random.nextDouble() < mean - whole ? 1 : 0);
    }

    /** One size from the request, a fractional size drawn as its neighbours so the mean blocks are exact. Two draws. */
    private static int drawSize(VeinRequest request, SplittableRandom random) {
        double size = request.size(random.nextInt(request.sizeCount()));
        double whole = Math.floor(size);
        return (int) whole + (random.nextDouble() < size - whole ? 1 : 0);
    }

    /** A vein while the chunk is being placed: its bits change as later veins take positions. */
    private static final class Builder {
        final int ordinal;
        final String ore;
        final Band band;
        final int minX;
        final int minY;
        final int minZ;
        final int sizeX;
        final int sizeY;
        final int sizeZ;
        final boolean guide;
        final long[] bits;
        int blocks;

        Builder(int ordinal, String ore, Band band, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, boolean guide) {
            this.ordinal = ordinal;
            this.ore = ore;
            this.band = band;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.guide = guide;
            this.bits = new long[(sizeX * sizeY * sizeZ + 63) / 64];
        }

        void set(int x, int y, int z) {
            int index = (y * sizeZ + z) * sizeX + x;
            long mask = 1L << (index & 63);
            if ((bits[index >>> 6] & mask) == 0) {
                bits[index >>> 6] |= mask;
                blocks++;
            }
        }

        void clear(int x, int y, int z) {
            int index = (y * sizeZ + z) * sizeX + x;
            long mask = 1L << (index & 63);
            if ((bits[index >>> 6] & mask) != 0) {
                bits[index >>> 6] &= ~mask;
                blocks--;
            }
        }

        VeinExtent build() {
            return new VeinExtent(ordinal, ore, band, minX, minY, minZ, sizeX, sizeY, sizeZ, bits, guide);
        }
    }
}
