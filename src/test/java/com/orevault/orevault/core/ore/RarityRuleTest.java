package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** §4.2's rule on expected blocks a chunk and mean blocks a vein (ADR 0009 step 8). */
class RarityRuleTest {
    @Test
    void aHundredBlocksAChunkIsCommonWhateverTheVein() {
        assertEquals(Rarity.COMMON, RarityRule.classify(100.0, 0.2));
        assertEquals(Rarity.COMMON, RarityRule.classify(1107.6, 22.15));
    }

    @Test
    void justUnderAHundredIsNotCommon() {
        assertEquals(Rarity.UNCOMMON, RarityRule.classify(99.99, 6.79));
        assertEquals(Rarity.RARE, RarityRule.classify(99.99, 0.46));
    }

    /** 4.5 itself is Uncommon: the rule is "Rare under 4.5". */
    @Test
    void theVeinThresholdIsStrict() {
        assertEquals(Rarity.UNCOMMON, RarityRule.classify(30, 4.5));
        assertEquals(Rarity.RARE, RarityRule.classify(30, 4.4999));
    }

    /** The old count-and-range rule made emerald Common: 100 attempts a chunk, under half a block each. */
    @Test
    void emeraldIsRareOnBlocksNotAttempts() {
        assertEquals(Rarity.RARE, RarityRule.classify(36.1, 0.46));
    }

    @Test
    void aBadInputIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> RarityRule.classify(Double.NaN, 4));
        assertThrows(IllegalArgumentException.class, () -> RarityRule.classify(10, -1));
        assertThrows(IllegalArgumentException.class, () -> RarityRule.classify(Double.POSITIVE_INFINITY, 4));
    }
}
