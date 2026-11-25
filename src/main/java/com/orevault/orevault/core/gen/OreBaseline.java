package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * What a team with no points spent finds in its Vault (§3.1 "Baseline ore distribution", ADR 0009 "One pure record"):
 * veins per chunk and configured size range for each band and rarity, and gravel and clay blobs per band. Nothing else
 * is stored. Density and the rarity shares are derived here from the counts and {@link VeinShape#meanBlocks}, and the
 * level curve and the generator read these methods, so a change to a count or a size moves the curve with it. That is
 * the 2026-09-27 lesson: the first build priced a curve for a Vault nobody had generated.
 *
 * <p>The numbers are §3.1's and the Balancer's; a change to them is a §3.1 change in the same PR. Counts are fractional,
 * as vanilla's are, and are rolled per chunk by the placer. The closed form ignores overlap between veins, which leaves
 * placed blocks about 1.7% under it in stone and 3.3% in deepslate; the 81-chunk placer test holds it against the real
 * placement within S12.
 *
 * @param ore veins per chunk and size range, by band and rarity
 * @param gravel gravel blobs per chunk, by band
 * @param clay clay blobs per chunk, by band
 */
public record OreBaseline(Map<Band, Map<Rarity, Veins>> ore, Map<Band, Blobs> gravel, Map<Band, Blobs> clay) {
    /**
     * The stone band's height in the base type and the deepslate band's, the third copy of the dimension heights (rule 8,
     * ADR 0009): the layer files, {@link VaultVariant} and these. A test compares them, so they fail together. The curve
     * reads the base type's figures; the expanded stone band spreads the same counts over 96 layers, 1.0% thinner.
     * The stone band was 245 layers until the surface came down to Y=100 (ADR 0009, amended 2026-11-21).
     */
    public static final int STONE_BAND_LAYERS = 95;
    public static final int DEEPSLATE_BAND_LAYERS = 63;

    private static final int BLOB_SIZE = 33;

    /**
     * §3.1's baseline. The stone band's counts are the 245-layer band's, 141, 82 and 33 veins and 9 gravel and 3 clay
     * blobs, times 95/245, written in that exact form so §3.1's density stays exact (3.0446% in closed form, which the
     * level curve reads, §4.3): 54.67, 31.80 and 12.80 veins, 3.49 and 1.16 blobs (ADR 0009, amended 2026-11-21). The
     * deepslate band did not move.
     */
    public static final OreBaseline BASE = new OreBaseline(
            Map.of(
                    Band.STONE, rarities(new Veins(141 * 95 / 245.0, 9, 12), new Veins(82 * 95 / 245.0, 7, 9),
                            new Veins(33 * 95 / 245.0, 5, 7)),
                    Band.DEEPSLATE, rarities(new Veins(65, 9, 12), new Veins(52, 7, 9), new Veins(25, 5, 7))),
            Map.of(Band.STONE, new Blobs(9 * 95 / 245.0, BLOB_SIZE), Band.DEEPSLATE, new Blobs(2, BLOB_SIZE)),
            Map.of(Band.STONE, new Blobs(3 * 95 / 245.0, BLOB_SIZE), Band.DEEPSLATE, new Blobs(1, BLOB_SIZE)));

    /** EnumMaps, not {@code Map.copyOf}: the placer iterates these, and a {@code Map.copyOf} map's order changes per JVM run (ADR 0003). */
    public OreBaseline {
        EnumMap<Band, Map<Rarity, Veins>> copy = new EnumMap<>(Band.class);
        for (Band band : Band.values()) {
            Map<Rarity, Veins> byRarity = ore.get(band);
            if (byRarity == null || !byRarity.keySet().containsAll(EnumSet.allOf(Rarity.class))) {
                throw new IllegalArgumentException("Every rarity needs veins in the " + band + " band");
            }
            copy.put(band, Collections.unmodifiableMap(new EnumMap<>(byRarity)));
            if (!gravel.containsKey(band) || !clay.containsKey(band)) {
                throw new IllegalArgumentException("Gravel and clay need a count in the " + band + " band");
            }
        }
        ore = Collections.unmodifiableMap(copy);
        gravel = Collections.unmodifiableMap(new EnumMap<>(gravel));
        clay = Collections.unmodifiableMap(new EnumMap<>(clay));
    }

    /**
     * One rarity's veins in one band.
     *
     * @param perChunk veins per chunk, before the edge factor, the clip and any node
     * @param minSize smallest configured size, drawn uniformly per vein
     * @param maxSize largest configured size
     */
    public record Veins(double perChunk, int minSize, int maxSize) {
        public Veins {
            if (perChunk < 0 || minSize < 1 || maxSize < minSize || maxSize > VeinShape.MAX_IN_CHUNK) {
                throw new IllegalArgumentException(perChunk + " veins of size " + minSize + " to " + maxSize);
            }
        }

        /** Mean blocks a vein places, sizes uniform over the range. */
        public double meanBlocks() {
            double sum = 0;
            for (int size = minSize; size <= maxSize; size++) {
                sum += VeinShape.meanBlocks(size);
            }
            return sum / (maxSize - minSize + 1);
        }
    }

    /**
     * Gravel or clay in one band. Not clipped and counted as stone for the 40% floor (§3.1).
     *
     * @param perChunk blobs per chunk
     * @param size configured size
     */
    public record Blobs(double perChunk, int size) {
        public Blobs {
            if (perChunk < 0 || size < 1) {
                throw new IllegalArgumentException(perChunk + " blobs of size " + size);
            }
        }
    }

    public Veins veins(Band band, Rarity rarity) {
        return ore.get(band).get(rarity);
    }

    /** Ore blocks a chunk for one rarity in one band, in closed form. */
    public double blocksPerChunk(Band band, Rarity rarity) {
        Veins veins = veins(band, rarity);
        return veins.perChunk() * veins.meanBlocks();
    }

    /** Ore blocks a chunk for the whole band. */
    public double blocksPerChunk(Band band) {
        double sum = 0;
        for (Rarity rarity : Rarity.values()) {
            sum += blocksPerChunk(band, rarity);
        }
        return sum;
    }

    /** A rarity's share of the band's ore, by placed blocks (§3.1: 68/25/7 in stone, 60/30/10 in deepslate). */
    public double share(Band band, Rarity rarity) {
        return blocksPerChunk(band, rarity) / blocksPerChunk(band);
    }

    /** The band's ore density in one dimension type: ore blocks over its volume (§3.1: 3% stone, 6% deepslate). */
    public double density(Band band, VaultVariant variant) {
        return blocksPerChunk(band) / (256.0 * bounds(band, variant).layers());
    }

    /**
     * How much a rarity's count is raised in a band to make up the blocks of veins cut at the band's floor and top (§3.1;
     * ADR 0009, amended 2026-11-21). Drawing a vein's middle from its ore's profile does not remove the cut: an ore with
     * mass at the band's floor or top loses its box's overhang there. So the factor is one over the blocks the rarity's
     * ores keep inside the band, mixed by their pick shares, from the same table the placer draws from and the vein's blocks
     * by layer about its box middle ({@link LayerSpread}). Placed blocks then match the closed form less the overlap. On
     * vanilla it is under 1% in the stone band and at most about 2.6% in deepslate, for common, whose iron peaks at the
     * band's top. It replaces the uniform {@code H / (H + h - 1)} of a band spread evenly.
     *
     * @param picker the picker of this band, whose profiles say where the rarity's veins sit
     */
    public double edgeFactor(Band band, Rarity rarity, OrePicker picker) {
        double[] kernel = LayerSpread.kernel(veins(band, rarity));
        BandBounds bounds = new BandBounds(picker.bandFloorY(), picker.bandTopY());
        double kept = LayerSpread.kept(LayerSpread.perVein(picker.table(rarity), kernel, bounds), kernel);
        return kept > 0 ? 1 / kept : 1.0;
    }

    private static BandBounds bounds(Band band, VaultVariant variant) {
        return variant.band(band).orElseThrow(() -> new IllegalArgumentException(variant.id() + " has no " + band + " band"));
    }

    private static Map<Rarity, Veins> rarities(Veins common, Veins uncommon, Veins rare) {
        return Map.of(Rarity.COMMON, common, Rarity.UNCOMMON, uncommon, Rarity.RARE, rare);
    }
}
