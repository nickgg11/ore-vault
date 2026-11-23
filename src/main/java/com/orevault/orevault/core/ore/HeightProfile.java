package com.orevault.orevault.core.ore;

import java.util.Arrays;

/**
 * Where one ore sits in the overworld: its mass per overworld layer, block-weighted and normalized to a total of 1 (§3.1
 * "Where each ore sits"; ADR 0009, amended 2026-11-21). The classifier fills it from sampled placement heights. The Vault
 * reads it at the overworld's own Y, one to one, in both dimension types: mass at overworld Y=10 belongs at Vault Y=10,
 * and mass outside a band is not that band's, so it is dropped there rather than folded in. It never changes how many
 * veins a band holds, only which ore of a rarity a vein is and where its middle sits.
 *
 * <p>Kept per layer, not in 24 bins of 16: vanilla ranges end on bin edges (diamond at 16, gold at 32, buried lapis at
 * 64), and binning smears a sliver of each into the next bin (Balancer, BAL-E4-4; Epic 4 triage ruling 4). Memory only,
 * never saved.
 *
 * <p>This package imports nothing of {@code core.gen} (ADR 0001), so a band's heights come in as plain ints from the
 * caller, which reads them from {@code VaultVariant}: no copy of the heights lives here (rule 8). The linear map onto the
 * old Y-63..245 column and the fold of mass below Y=1 into the base type's lowest slab went with that column.
 *
 * @param sourceFloorY the overworld Y of {@code masses[0]}; -64 on vanilla
 * @param masses mass per overworld layer, upward
 */
public record HeightProfile(int sourceFloorY, double[] masses) {
    /** Height of each Vault slab the density check reads, counted up from a band's floor (ADR 0009 Tests). */
    public static final int SLAB = 16;

    public HeightProfile {
        double total = 0;
        for (double mass : masses) {
            if (!(mass >= 0)) {
                throw new IllegalArgumentException("A negative or NaN mass");
            }
            total += mass;
        }
        if (masses.length == 0 || total <= 0) {
            throw new IllegalArgumentException("A profile with no mass");
        }
        masses = masses.clone();
        for (int i = 0; i < masses.length; i++) {
            masses[i] /= total;
        }
    }

    /** Even over the overworld's range, for an override that names an ore with no feature data (ADR 0009 step 8). */
    public static HeightProfile flat(int sourceFloorY, int layers) {
        double[] masses = new double[layers];
        Arrays.fill(masses, 1.0);
        return new HeightProfile(sourceFloorY, masses);
    }

    @Override
    public double[] masses() {
        return masses.clone();
    }

    /** The overworld height the profile spans. */
    public int sourceLayers() {
        return masses.length;
    }

    /** The mass at one Y, read at the overworld's own Y; 0 outside the overworld the profile spans. */
    public double massAt(int y) {
        int index = y - sourceFloorY;
        return index >= 0 && index < masses.length ? masses[index] : 0;
    }

    /**
     * The mass at each layer of {@code bottomY..topY}, index 0 at {@code bottomY}: a band's or a region's in-band mass per
     * layer, which the ore draw sums and the middle draw reads (ADR 0009, amended 2026-11-21). Not renormalized; mass
     * outside the range is simply not in it.
     */
    public double[] layers(int bottomY, int topY) {
        if (topY < bottomY) {
            throw new IllegalArgumentException("An empty range Y" + bottomY + ".." + topY);
        }
        double[] layers = new double[topY - bottomY + 1];
        for (int y = bottomY; y <= topY; y++) {
            layers[y - bottomY] = massAt(y);
        }
        return layers;
    }

    /** Which slab of a band a layer falls in, the top partial slab merged; the layer is clamped into the band first. */
    public static int slabOf(int layerY, int bandFloorY, int bandTopY) {
        int clamped = Math.max(bandFloorY, Math.min(bandTopY, layerY));
        int slabCount = Math.max(1, (bandTopY - bandFloorY + 1) / SLAB);
        return Math.min(slabCount - 1, (clamped - bandFloorY) / SLAB);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HeightProfile that && sourceFloorY == that.sourceFloorY && Arrays.equals(masses, that.masses);
    }

    @Override
    public int hashCode() {
        return 31 * sourceFloorY + Arrays.hashCode(masses);
    }

    @Override
    public String toString() {
        return "HeightProfile[from Y=" + sourceFloorY + ", " + masses.length + " layers]";
    }
}
