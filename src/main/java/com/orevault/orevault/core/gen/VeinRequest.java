package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.Rarity;
import java.util.Arrays;
import java.util.Objects;

/**
 * One batch of veins the placer rolls in a chunk: how many on average, which configured sizes, and which layers their
 * lowest layer is drawn against. The baseline is one request per band and rarity; generation nodes (T9) express
 * themselves as changes to these or as extra requests, such as Deep Veins' lowest 30 layers or Guide Veins, which is what
 * lets the floor clip be computed for them (ADR 0009 Consequences).
 *
 * @param band the band the veins are cut to
 * @param rarity the rarity the ore is picked from
 * @param perChunk mean veins a chunk, every factor already applied; rolled as floor plus a fractional draw
 * @param sizes configured sizes, one drawn uniformly per vein; a fractional size draws its two neighbours so its mean
 *     blocks are exact (Balancer, BAL-E4-10)
 * @param regionBottomY the lowest layer of the region the veins are spread through, inside the band
 * @param regionTopY the region's top layer
 * @param guide whether these are guide veins (§6.1)
 */
public record VeinRequest(Band band, Rarity rarity, double perChunk, double[] sizes, int regionBottomY, int regionTopY,
        boolean guide) {
    public VeinRequest {
        Objects.requireNonNull(band);
        Objects.requireNonNull(rarity);
        if (!(perChunk >= 0) || sizes.length == 0 || regionTopY < regionBottomY) {
            throw new IllegalArgumentException("A request of " + perChunk + " veins over Y" + regionBottomY + ".." + regionTopY);
        }
        for (double size : sizes) {
            if (!(size >= 1) || Math.ceil(size) > VeinShape.MAX_IN_CHUNK) {
                throw new IllegalArgumentException("Size " + size + ": over " + VeinShape.MAX_IN_CHUNK + " must be split first (ADR 0004)");
            }
        }
        sizes = sizes.clone();
    }

    @Override
    public double[] sizes() {
        return sizes.clone();
    }

    int sizeCount() {
        return sizes.length;
    }

    double size(int index) {
        return sizes[index];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VeinRequest that && band == that.band && rarity == that.rarity && perChunk == that.perChunk
                && Arrays.equals(sizes, that.sizes) && regionBottomY == that.regionBottomY && regionTopY == that.regionTopY
                && guide == that.guide;
    }

    @Override
    public int hashCode() {
        return Objects.hash(band.ordinal(), rarity.ordinal(), perChunk, Arrays.hashCode(sizes), regionBottomY, regionTopY, guide);
    }
}
