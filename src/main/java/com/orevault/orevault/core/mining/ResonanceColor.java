package com.orevault.orevault.core.mining;

/**
 * The Resonance colour (§11 Resonance Orb Entity: Resonance is blue and cyan). {@link #RESONANCE_COLOR} is the one value
 * the orb, the Tome header (T8) and the Resonance bar share, so they cannot drift apart. The orb pulses from it to
 * {@link #RESONANCE_DEEP} on vanilla's orb timing, so it moves as an XP orb does and still reads as something else
 * (Designer, #260).
 */
public final class ResonanceColor {
    /** ARGB, the old build's {@code RESONANCE_COLOR}. */
    public static final int RESONANCE_COLOR = 0xFF4FC3F7;
    /** ARGB, the deep end of the orb's pulse. */
    public static final int RESONANCE_DEEP = 0xFF1E88E5;

    private ResonanceColor() {
    }

    /**
     * The orb's colour at an age in ticks: {@code (sin(age / 2) + 1) / 2} of the way from the light end to the deep end,
     * the sine and period of vanilla's {@code ExperienceOrbRenderer}.
     */
    public static int pulse(float ageInTicks) {
        return blend((float) ((Math.sin(ageInTicks / 2.0) + 1.0) * 0.5));
    }

    /** Each channel {@code t} of the way from {@link #RESONANCE_COLOR} to {@link #RESONANCE_DEEP}, rounded; opaque. */
    public static int blend(float t) {
        float clamped = Math.clamp(t, 0.0F, 1.0F);
        int color = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int from = RESONANCE_COLOR >> shift & 0xFF;
            int to = RESONANCE_DEEP >> shift & 0xFF;
            color |= Math.round(from + (to - from) * clamped) << shift;
        }
        return color;
    }
}
