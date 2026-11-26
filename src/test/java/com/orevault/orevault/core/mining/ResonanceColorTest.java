package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The one Resonance colour and the orb's pulse (#260, Designer; Manager ruling 1). */
class ResonanceColorTest {
    /** Planted break: the Tome header and the orb read this one value, so a change here is a visible change. */
    @Test
    void theResonanceColourIsTheOldBuilds() {
        assertEquals(0xFF4FC3F7, ResonanceColor.RESONANCE_COLOR);
        assertEquals(0xFF1E88E5, ResonanceColor.RESONANCE_DEEP);
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
        // Each channel halfway, rounded: 0x4F..0x1E = 54.5, so 0x37, 0xC3..0x88 = 0xA6, 0xF7..0xE5 = 0xEE.
        assertEquals(0xFF37A6EE, ResonanceColor.blend(0.5F));
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
