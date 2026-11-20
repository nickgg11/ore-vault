package com.orevault.orevault.core.ore;

/**
 * §4.2's rarity rule: Common at 100 or more expected blocks a chunk over the features the classifier uses for an ore;
 * otherwise Rare when its mean vein places under 4.5 blocks, else Uncommon (ADR 0009 step 8). It runs on placed blocks,
 * not on attempts: the old count-and-range rule made emerald Common from 100 attempts a chunk of under half a block each.
 * On vanilla lapis has the thinnest margin, 4.97 blocks a vein against 4.5.
 */
public final class RarityRule {
    /** Expected blocks a chunk at and above which an ore is Common. */
    public static final double COMMON_BLOCKS_PER_CHUNK = 100.0;
    /** Mean blocks a vein under which an ore that is not Common is Rare. */
    public static final double RARE_BLOCKS_PER_VEIN = 4.5;

    private RarityRule() {
    }

    public static Rarity classify(double expectedBlocksPerChunk, double meanBlocksPerVein) {
        if (!Double.isFinite(expectedBlocksPerChunk) || expectedBlocksPerChunk < 0
                || !Double.isFinite(meanBlocksPerVein) || meanBlocksPerVein < 0) {
            throw new IllegalArgumentException("No rarity for " + expectedBlocksPerChunk + " blocks a chunk, "
                    + meanBlocksPerVein + " a vein");
        }
        if (expectedBlocksPerChunk >= COMMON_BLOCKS_PER_CHUNK) {
            return Rarity.COMMON;
        }
        return meanBlocksPerVein < RARE_BLOCKS_PER_VEIN ? Rarity.RARE : Rarity.UNCOMMON;
    }
}
