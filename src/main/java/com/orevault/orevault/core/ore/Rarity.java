package com.orevault.orevault.core.ore;

/**
 * The three ore rarities of §4.2. The baseline's counts are per rarity, never per ore (ADR 0009 "Counts per rarity"), so
 * a pack with forty ores places as many veins as vanilla.
 *
 * <p>Each rarity's base Resonance (2, 5 and 12.5, §4.2) joins this enum with its first reader, the level curve in E5-S1
 * (#240), as a {@code double} so that 12.5 is exact (Balancer, Epic 4 plan review BAL-E4-11).
 */
public enum Rarity {
    COMMON,
    UNCOMMON,
    RARE
}
