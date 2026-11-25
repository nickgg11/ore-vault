package com.orevault.orevault.core.ore;

/**
 * The three ore rarities of §4.2. The baseline's counts are per rarity, never per ore (ADR 0009 "Counts per rarity"), so
 * a pack with forty ores places as many veins as vanilla.
 *
 * <p>Each rarity carries its base Resonance (§4.2): the one constant the orb and the level curve read, and the value
 * §4.2's table states (S40). A {@code double}, so that 12.5 is exact (Balancer, Epic 4 plan review BAL-E4-11).
 */
public enum Rarity {
    COMMON(2.0),
    UNCOMMON(5.0),
    RARE(12.5);

    private final double baseResonance;

    Rarity(double baseResonance) {
        this.baseResonance = baseResonance;
    }

    /** Resonance an ore of this rarity pays when broken by hand in a Vault, before any node (§4.2). */
    public double baseResonance() {
        return baseResonance;
    }
}
