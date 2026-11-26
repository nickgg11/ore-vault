package com.orevault.orevault.core.mining;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Everything a Vault orb decides that needs no level (§4.2, §11 Resonance Orb Entity), so it runs in JUnit. The numbers are
 * vanilla's {@code ExperienceOrb}'s: an orb should behave as an orb and differ only in who it follows and pays.
 *
 * <p>"Member" has one definition, {@link #isMember}: the server alone decides it, and an orb with no team has none.
 */
public final class OrbRules {
    /** Vanilla's orb radius ({@code XpOrbTargetingEvent(this, 8.0)}, §4.2). Resonance Magnetism raises it (#347). */
    public static final double BASE_RADIUS = 8.0;
    /**
     * Ticks before an unpicked orb despawns: vanilla's 6000, five minutes. Permanent orbs would pile up entities and lag
     * the server (Nick, 2026-11-26, §4.2); Hoarder's Instinct stops the ageing (#347).
     */
    public static final int LIFETIME = 6000;
    /** Ticks between merge scans, vanilla's {@code ENTITY_SCAN_PERIOD}. */
    public static final int SCAN_PERIOD = 20;
    /** How far past its box an orb reaches to merge, vanilla's {@code ORB_MERGE_DISTANCE}. */
    public static final double MERGE_REACH = 0.5;

    private static final double MAX_PULL = 0.1;
    /**
     * Thousandths of a Resonance at which each larger sprite starts, lowest first; the count passed is the sprite. Vanilla's
     * sheet draws sprites 0 to 10 at 8, 10, 12, 12, 12, 14, 14, 14, 16, 16, 16 pixels, with a pink core from sprite 3, so
     * the three ore tiers land on sprites that differ in size and in core: common 2 on sprite 0 (8 pixels, no core),
     * uncommon 5 on 4 (12, small core), rare 12.5 on 7 (14, large core). Each tier keeps its sprite up to about +50 percent
     * and the upper two down to -20 percent, so ordinary bonuses do not move it. Deep Harvest's halves (1, 2.5, 6.25) land
     * on 0, 0 and 4, Tithe's x1.75 a step or more larger, Vault Echo on 8 and 9, and caches of 100 or more on 10 (Nick,
     * 2026-11-26).
     */
    private static final long[] ICON_THRESHOLDS = {3_000, 3_250, 3_500, 4_000, 7_500, 9_000, 10_000, 20_000, 50_000, 100_000};

    /** The fields an orb has after a merge. */
    public record Merged(long value, int orbCount, int age) {
    }

    private OrbRules() {
    }

    /** Whether a player of {@code playerTeam} may attract and collect an orb of {@code orbTeam}. Never for a team-less orb. */
    public static boolean isMember(@Nullable UUID orbTeam, UUID playerTeam) {
        return orbTeam != null && orbTeam.equals(playerTeam);
    }

    /**
     * Speed added toward the player per tick: vanilla's {@code (1 - d / r)^2 * 0.1}. Vanilla keeps a player it already
     * follows up to the radius and the formula reaches 0 there; past it, nothing pulls.
     */
    public static double pull(double distance, double radius) {
        if (distance >= radius) {
            return 0.0;
        }
        double power = 1.0 - distance / radius;
        return power * power * MAX_PULL;
    }

    /** Any two orbs of one team merge, whatever their values (vanilla needs equal values; ours sum). */
    public static boolean canMerge(@Nullable UUID a, @Nullable UUID b) {
        return a != null && a.equals(b);
    }

    /** Value and {@code orb_count} summed, saturating; age the younger of the two, as vanilla's merge. */
    public static Merged merge(long valueA, int countA, int ageA, long valueB, int countB, int ageB) {
        long value = valueB > Long.MAX_VALUE - valueA ? Long.MAX_VALUE : valueA + valueB;
        int count = countB > Integer.MAX_VALUE - countA ? Integer.MAX_VALUE : countA + countB;
        return new Merged(value, count, Math.min(ageA, ageB));
    }

    /**
     * The sprite on vanilla's orb sheet for a value in thousandths: common 2 is sprite 0, uncommon 5 sprite 4, rare 12.5
     * sprite 7, and merged orbs grow to sprite 10 at 100 Resonance. Vanilla's own thresholds put the three tiers on sprites
     * 0, 1 and 2, which Nick could not tell apart in game (2026-11-26).
     */
    public static int icon(long valueThousandths) {
        int icon = 0;
        while (icon < ICON_THRESHOLDS.length && valueThousandths >= ICON_THRESHOLDS[icon]) {
            icon++;
        }
        return icon;
    }
}
