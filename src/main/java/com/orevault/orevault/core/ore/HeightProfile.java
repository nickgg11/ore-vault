package com.orevault.orevault.core.ore;

import java.util.Arrays;

/**
 * Where one ore sits in the overworld: its mass per overworld layer, block-weighted and normalized to a total of 1 (ADR
 * 0009 "Which ore a vein is"). The classifier fills it from sampled placement heights; the identity pick reads it mapped
 * onto the Vault's column. It never changes how many veins a band holds, only which ore of a rarity a vein is.
 *
 * <p>Kept per layer, not in 24 bins of 16: vanilla ranges end on bin edges (diamond at 16, gold at 32, buried lapis at
 * 64), and binning smears a sliver of each into the next bin, which moved figures Nick chose on (Balancer, BAL-E4-4;
 * Epic 4 triage ruling 4). Memory only, never saved.
 *
 * <p>This package imports nothing of {@code core.gen} (ADR 0001), so the Vault's heights come in as plain ints from the
 * caller, which reads them from {@code VaultVariant}: no copy of the heights lives here (rule 8).
 *
 * @param sourceFloorY the overworld Y of {@code masses[0]}; -64 on vanilla
 * @param masses mass per overworld layer, upward
 */
public record HeightProfile(int sourceFloorY, double[] masses) {
    /** Height of each Vault slab the pick reads, counted up from a band's floor (ADR 0009 Tests). */
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

    /** The overworld height the profile spans, the source of the linear map. */
    public int sourceLayers() {
        return masses.length;
    }

    /**
     * The profile's mass in each 16-layer slab of one band, counted up from the band's floor, a partial top slab merged
     * into the one below (ADR 0009 Tests). The overworld's whole range maps linearly onto the Vault column
     * {@code columnFloorY..columnTopY} (Y-63..245 in both dimension types, so an ore sits at the same height whichever type
     * a team has); each overworld layer's mass is spread evenly over the stretch it maps to, and split between slabs by
     * exact overlap. Mass that maps below the band goes into its lowest slab when {@code foldBelow} (the base type's stone
     * band, which has no deepslate below it) and is dropped otherwise, since it belongs to the band below; mass above the
     * band is dropped.
     */
    public double[] slabs(int columnFloorY, int columnTopY, int bandFloorY, int bandTopY, boolean foldBelow) {
        if (columnTopY < columnFloorY || bandTopY < bandFloorY) {
            throw new IllegalArgumentException("An empty column or band");
        }
        int bandLayers = bandTopY - bandFloorY + 1;
        int slabCount = Math.max(1, bandLayers / SLAB);
        double[] slabs = new double[slabCount];
        double scale = (columnTopY + 1.0 - columnFloorY) / masses.length;
        for (int i = 0; i < masses.length; i++) {
            if (masses[i] == 0) {
                continue;
            }
            double from = columnFloorY + i * scale;
            double to = from + scale;
            double density = masses[i] / scale;
            if (foldBelow && from < bandFloorY) {
                slabs[0] += density * (Math.min(to, bandFloorY) - from);
            }
            for (int slab = 0; slab < slabCount; slab++) {
                double slabFrom = bandFloorY + slab * (double) SLAB;
                double slabTo = slab == slabCount - 1 ? bandTopY + 1.0 : slabFrom + SLAB;
                double overlap = Math.min(to, slabTo) - Math.max(from, slabFrom);
                if (overlap > 0) {
                    slabs[slab] += density * overlap;
                }
            }
        }
        return slabs;
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
