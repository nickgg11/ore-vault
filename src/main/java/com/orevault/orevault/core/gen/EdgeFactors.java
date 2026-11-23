package com.orevault.orevault.core.gen;

import com.orevault.orevault.core.ore.OrePicker;
import com.orevault.orevault.core.ore.Rarity;
import java.util.Arrays;
import java.util.Map;

/**
 * Each band's and rarity's edge factor ({@link OreBaseline#edgeFactor}), computed once on the main thread when the
 * generation snapshot is built and carried in it (ADR 0009, amended 2026-11-21: the edge factor's inputs are built on the
 * main thread with the pickers; ADR 0003). The factor depends only on the baseline and the pickers, and computing it
 * walks every ore's middle distribution through the kernel, so the generator reads these six numbers per chunk instead
 * of recomputing them on the worker (Reviewer, R-6). Immutable.
 */
public final class EdgeFactors {
    /** Every factor 1: a pack with no classified ore, which places no ore, and a band the variant lacks. */
    public static final EdgeFactors NONE = new EdgeFactors(ones());

    /** By band ordinal, then rarity ordinal. */
    private final double[][] factors;

    private EdgeFactors(double[][] factors) {
        this.factors = factors;
    }

    /**
     * The factors of every band the variant has from that band's picker; 1 for a band with no picker.
     *
     * @param pickers the variant's published pickers, or none for a pack with no classified ore
     */
    public static EdgeFactors of(OreBaseline baseline, VaultVariant variant, Map<Band, OrePicker> pickers) {
        double[][] factors = ones();
        for (Band band : Band.values()) {
            OrePicker picker = pickers.get(band);
            if (variant.band(band).isEmpty() || picker == null) {
                continue;
            }
            for (Rarity rarity : Rarity.values()) {
                factors[band.ordinal()][rarity.ordinal()] = baseline.edgeFactor(band, rarity, picker);
            }
        }
        return new EdgeFactors(factors);
    }

    public double of(Band band, Rarity rarity) {
        return factors[band.ordinal()][rarity.ordinal()];
    }

    private static double[][] ones() {
        double[][] ones = new double[Band.values().length][Rarity.values().length];
        for (double[] row : ones) {
            Arrays.fill(row, 1.0);
        }
        return ones;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EdgeFactors that && Arrays.deepEquals(factors, that.factors);
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(factors);
    }

    @Override
    public String toString() {
        return "EdgeFactors" + Arrays.deepToString(factors);
    }
}
