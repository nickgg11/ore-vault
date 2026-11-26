package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The one Resonance colour and the orb's pulse (#260, Designer; Manager ruling 1). */
class ResonanceColorTest {
    /** Planted break: the Tome header and the orb read this one value, so a change here is a visible change. */
    @Test
    void theResonanceColourIsViolet() {
        assertEquals(0xFFB36BFF, ResonanceColor.RESONANCE_COLOR);
        assertEquals(0xFF7A3DF0, ResonanceColor.RESONANCE_DEEP);
    }

    /** Nick, 2026-11-26: purple, unlike vanilla's green XP. Both ends are blue-dominant, so they never read as green. */
    @Test
    void bothEndsAreVioletNotGreen() {
        for (int color : new int[] {ResonanceColor.RESONANCE_COLOR, ResonanceColor.RESONANCE_DEEP}) {
            int red = color >> 16 & 0xFF;
            int green = color >> 8 & 0xFF;
            int blue = color & 0xFF;
            assertTrue(blue > red && red > green, Integer.toHexString(color));
        }
    }

    /** {@code (sin(age / 2) + 1) / 2}: halfway at age 0, the deep end at age pi, the light end at age 3 pi. */
    @Test
    void thePulseRunsOnVanillasOrbTiming() {
        assertEquals(ResonanceColor.blend(0.5F), ResonanceColor.pulse(0.0F));
        assertEquals(ResonanceColor.RESONANCE_DEEP, ResonanceColor.pulse((float) Math.PI));
        assertEquals(ResonanceColor.RESONANCE_COLOR, ResonanceColor.pulse((float) (3 * Math.PI)));
    }

    @Test
    void theBlendRunsFromTheLightEndToTheDeepEnd() {
        assertEquals(ResonanceColor.RESONANCE_COLOR, ResonanceColor.blend(0.0F));
        assertEquals(ResonanceColor.RESONANCE_DEEP, ResonanceColor.blend(1.0F));
        // Each channel halfway, rounded: 0xB3..0x7A = 150.5, so 0x97; 0x6B..0x3D = 0x54; 0xFF..0xF0 = 247.5, so 0xF8.
        assertEquals(0xFF9754F8, ResonanceColor.blend(0.5F));
    }

    @Test
    void theBlendIsClampedAndOpaque() {
        assertEquals(ResonanceColor.RESONANCE_COLOR, ResonanceColor.blend(-1.0F));
        assertEquals(ResonanceColor.RESONANCE_DEEP, ResonanceColor.blend(2.0F));
        for (float t = 0.0F; t <= 1.0F; t += 0.125F) {
            assertEquals(0xFF, ResonanceColor.blend(t) >>> 24, "alpha at " + t);
        }
    }
}
