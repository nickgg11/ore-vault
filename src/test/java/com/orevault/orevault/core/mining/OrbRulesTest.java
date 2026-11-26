package com.orevault.orevault.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The orb's decisions that need no level (§4.2, §11 Resonance Orb Entity; sketch #260). */
class OrbRulesTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final UUID OTHER = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");

    @Test
    void theConstantsAreVanillasOrb() {
        assertEquals(8.0, OrbRules.BASE_RADIUS);
        assertEquals(6000, OrbRules.LIFETIME);
        assertEquals(20, OrbRules.SCAN_PERIOD);
        assertEquals(0.5, OrbRules.MERGE_REACH);
    }

    @Test
    void aMemberIsOneWhoseTeamIsTheOrbs() {
        assertTrue(OrbRules.isMember(TEAM, TEAM));
        assertFalse(OrbRules.isMember(TEAM, OTHER));
    }

    /** Planted break: answering true here pays a team-less orb to whoever walks past. */
    @Test
    void anOrbWithNoTeamHasNoMembers() {
        assertFalse(OrbRules.isMember(null, TEAM));
    }

    @Test
    void thePullIsVanillasCurve() {
        assertEquals(0.1, OrbRules.pull(0.0, 8.0), 1e-12);
        assertEquals(0.025, OrbRules.pull(4.0, 8.0), 1e-12);
        assertEquals(0.1 * 0.75 * 0.75, OrbRules.pull(2.0, 8.0), 1e-12);
    }

    @Test
    void thereIsNoPullAtOrPastTheRadius() {
        assertEquals(0.0, OrbRules.pull(8.0, 8.0));
        assertEquals(0.0, OrbRules.pull(9.5, 8.0));
    }

    @Test
    void onlyOrbsOfOneTeamMerge() {
        assertTrue(OrbRules.canMerge(TEAM, TEAM));
        assertFalse(OrbRules.canMerge(TEAM, OTHER));
        assertFalse(OrbRules.canMerge(null, TEAM));
        assertFalse(OrbRules.canMerge(TEAM, null));
        assertFalse(OrbRules.canMerge(null, null));
    }

    @Test
    void aMergeSumsValueAndCountAndKeepsTheYoungerAge() {
        assertEquals(new OrbRules.Merged(14_500L, 4, 30), OrbRules.merge(12_500L, 3, 100, 2_000L, 1, 30));
    }

    @Test
    void aMergeSaturatesRatherThanOverflowing() {
        assertEquals(new OrbRules.Merged(Long.MAX_VALUE, Integer.MAX_VALUE, 0),
                OrbRules.merge(Long.MAX_VALUE - 1, Integer.MAX_VALUE - 1, 0, 5L, 5, 10));
    }

    /** The ore tiers land on sprites that differ in size and core: common 2 on 0, uncommon 5 on 4, rare 12.5 on 7. */
    @Test
    void theOreTiersAreVisiblyDifferentSprites() {
        assertEquals(0, OrbRules.icon(2_000L));
        assertEquals(4, OrbRules.icon(5_000L));
        assertEquals(7, OrbRules.icon(12_500L));
    }

    /** Each tier keeps its sprite to about +50 percent, and the upper two to -20 percent, so ordinary bonuses do not move it. */
    @Test
    void aTierKeepsItsSpriteThroughOrdinaryBonuses() {
        assertEquals(0, OrbRules.icon(2_999L));
        assertEquals(4, OrbRules.icon(4_000L));
        assertEquals(4, OrbRules.icon(7_499L));
        assertEquals(7, OrbRules.icon(10_000L));
        assertEquals(7, OrbRules.icon(19_999L));
    }

    /** Each band's floor, in thousandths: 3, 3.25, 3.5, 4, 7.5, 9, 10, 20, 50, 100 Resonance. */
    @Test
    void theBandsStartAtTheirFloors() {
        long[] floors = {3_000, 3_250, 3_500, 4_000, 7_500, 9_000, 10_000, 20_000, 50_000, 100_000};
        for (int i = 0; i < floors.length; i++) {
            assertEquals(i, OrbRules.icon(floors[i] - 1), "below floor " + floors[i]);
            assertEquals(i + 1, OrbRules.icon(floors[i]), "at floor " + floors[i]);
        }
    }

    /** Other payments around the tiers: Stone Memory, Deep Harvest, Tithe, Vault Echo, caches. */
    @Test
    void otherPaymentsSitAroundTheTiers() {
        assertEquals(0, OrbRules.icon(0L));
        assertEquals(0, OrbRules.icon(50L));
        assertEquals(0, OrbRules.icon(1_000L));
        assertEquals(0, OrbRules.icon(2_500L));
        assertEquals(4, OrbRules.icon(6_250L));
        assertEquals(3, OrbRules.icon(3_500L));
        assertEquals(5, OrbRules.icon(8_750L));
        assertEquals(8, OrbRules.icon(21_875L));
        assertEquals(8, OrbRules.icon(25_000L));
        assertEquals(9, OrbRules.icon(50_000L));
        assertEquals(10, OrbRules.icon(150_000L));
        assertEquals(10, OrbRules.icon(Long.MAX_VALUE));
    }
}
