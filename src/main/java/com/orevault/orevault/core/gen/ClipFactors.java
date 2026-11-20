package com.orevault.orevault.core.gen;

/**
 * The 40% stone floor's two factors (§3.1, ADR 0009 "Counts per rarity, and the floor clip"): every rarity's count in a
 * band is multiplied by its band's factor, {@code min(1, 0.60 / densest)}, so the mix never shifts. Computed on the main
 * thread and carried in the generation snapshot (ADR 0003); the builder from the team's generation tiers comes with
 * E4-S3 (#247). With no generation nodes both are 1.
 *
 * @param stone the stone band's factor
 * @param deepslate the deepslate band's factor, carried but never read in the base type
 */
public record ClipFactors(double stone, double deepslate) {
    public static final ClipFactors NONE = new ClipFactors(1, 1);

    public ClipFactors {
        if (!(stone > 0 && stone <= 1 && deepslate > 0 && deepslate <= 1)) {
            throw new IllegalArgumentException("Clip factors " + stone + ", " + deepslate);
        }
    }

    public double of(Band band) {
        return band == Band.STONE ? stone : deepslate;
    }
}
