package com.orevault.orevault.core.mining;

/**
 * The Resonance colour (§11 Resonance Orb Entity: Resonance is violet). {@link #RESONANCE_COLOR} is the one value the
 * orb, the Tome header (T8) and the Resonance bar share, so they cannot drift apart. The orb pulses from it to
 * {@link #RESONANCE_DEEP} on vanilla's orb timing, so it moves as an XP orb does and still reads as something else
 * (Designer, #260).
 *
 * <p>Violet sits opposite vanilla's yellow-green XP on the blue-yellow axis that red-green colour blindness keeps, and is
 * far darker, so the two orbs differ in greyscale too; it is blue-shifted from portal tier 3's {@code #A038D0} and from
 * Animus's red. Nick chose purple over the first build's cyan and blue on 2026-11-26 ({@code DESIGN_LOG.md}).
 */
public final class ResonanceColor {
    /** ARGB, light violet (hue about 269), the Resonance colour of the whole mod and the light end of the orb's pulse. */
    public static final int RESONANCE_COLOR = 0xFFB36BFF;
    /** ARGB, deep violet (hue about 260), the deep end of the orb's pulse; light enough to stay visible at the orb's alpha. */
    public static final int RESONANCE_DEEP = 0xFF7A3DF0;

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
