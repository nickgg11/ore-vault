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
    private static final long THOUSANDTHS = 1_000L;
    /** Vanilla's {@code ExperienceOrb#getIcon} thresholds, lowest first; the index past the last one passed is the sprite. */
    private static final long[] ICON_THRESHOLDS = {3, 7, 17, 37, 73, 149, 307, 617, 1237, 2477};

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
     * The sprite on vanilla's orb sheet: vanilla's thresholds applied to whole Resonance, floored, so a common ore's 2 is
     * sprite 0, an uncommon 5 sprite 1, a rare 12.5 sprite 2, and merged orbs grow.
     */
    public static int icon(long valueThousandths) {
        long whole = valueThousandths / THOUSANDTHS;
        int icon = 0;
        while (icon < ICON_THRESHOLDS.length && whole >= ICON_THRESHOLDS[icon]) {
            icon++;
        }
        return icon;
    }
}
