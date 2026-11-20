package com.orevault.orevault.core.gen;

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
 * as vanilla's are, and are rolled per chunk by the placer. The closed form ignores overlap between veins, about 1.5% of
 * blocks at 3%; the 81-chunk placer test holds it against the real placement.
 *
 * @param ore veins per chunk and size range, by band and rarity
 * @param gravel gravel blobs per chunk, by band
 * @param clay clay blobs per chunk, by band
 */
public record OreBaseline(Map<Band, Map<Rarity, Veins>> ore, Map<Band, Blobs> gravel, Map<Band, Blobs> clay) {
    /**
     * The stone band's height in the base type and the deepslate band's, the third copy of the dimension heights (rule 8,
     * ADR 0009): the dimension type files, {@link VaultVariant} and these. A test compares them, so they fail together.
     * The curve reads the base type's figures; the expanded stone band spreads the same counts over 246 layers.
     */
    public static final int STONE_BAND_LAYERS = 245;
    public static final int DEEPSLATE_BAND_LAYERS = 63;

    private static final int BLOB_SIZE = 33;

    /** §3.1's baseline. */
    public static final OreBaseline BASE = new OreBaseline(
            Map.of(
                    Band.STONE, rarities(new Veins(141, 9, 12), new Veins(82, 7, 9), new Veins(33, 5, 7)),
                    Band.DEEPSLATE, rarities(new Veins(65, 9, 12), new Veins(52, 7, 9), new Veins(25, 5, 7))),
            Map.of(Band.STONE, new Blobs(9, BLOB_SIZE), Band.DEEPSLATE, new Blobs(2, BLOB_SIZE)),
            Map.of(Band.STONE, new Blobs(3, BLOB_SIZE), Band.DEEPSLATE, new Blobs(1, BLOB_SIZE)));

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
     * How much a rarity's count is raised in a band to make up the blocks of veins cut at the band's floor and top (ADR
     * 0009 "Height"). A box {@code h} layers tall, whose lowest layer is drawn from the {@code H + h - 1} positions that
     * leave a layer inside the band, keeps on average {@code H / (H + h - 1)} of its blocks; the factor is the inverse of
     * that, averaged over the rarity's sizes weighted by blocks. It is about +0.4 to +0.65% in the stone band and +1.6 to
     * +2.5% in deepslate (Balancer, BAL-E4-5); placed blocks then match the closed form.
     */
    public double edgeFactor(Band band, Rarity rarity, VaultVariant variant) {
        int height = bounds(band, variant).layers();
        Veins veins = veins(band, rarity);
        double kept = 0;
        double weight = 0;
        for (int size = veins.minSize(); size <= veins.maxSize(); size++) {
            double blocks = VeinShape.meanBlocks(size);
            double boxHeight = Math.max(1.0, VeinShape.blockWeightedBoxHeight(size));
            kept += blocks * height / (height + boxHeight - 1);
            weight += blocks;
        }
        return weight == 0 ? 1.0 : weight / kept;
    }

    private static BandBounds bounds(Band band, VaultVariant variant) {
        return variant.band(band).orElseThrow(() -> new IllegalArgumentException(variant.id() + " has no " + band + " band"));
    }

    private static Map<Rarity, Veins> rarities(Veins common, Veins uncommon, Veins rare) {
        return Map.of(Rarity.COMMON, common, Rarity.UNCOMMON, uncommon, Rarity.RARE, rare);
    }
}
