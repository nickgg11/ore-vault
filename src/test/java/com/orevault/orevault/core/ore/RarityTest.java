package com.orevault.orevault.core.ore;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** §4.2's base Resonance, one constant per rarity (S40). */
class RarityTest {
    @Test
    void baseResonanceIsTheSpecTable() {
        assertEquals(2.0, Rarity.COMMON.baseResonance());
        assertEquals(5.0, Rarity.UNCOMMON.baseResonance());
        assertEquals(12.5, Rarity.RARE.baseResonance());
    }
}
