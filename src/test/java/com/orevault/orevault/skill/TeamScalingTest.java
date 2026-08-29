package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Pure-logic tests for the §4.2 team pool scaling formula. */
class TeamScalingTest {

    @Test
    void multiplierMatchesTheSpecTable() {
        assertEquals(1.0, TeamScaling.multiplier(1), 1e-9);
        assertEquals(1.1, TeamScaling.multiplier(2), 1e-9);
        assertEquals(1.2, TeamScaling.multiplier(3), 1e-9);
        assertEquals(1.4, TeamScaling.multiplier(5), 1e-9);
    }

    @Test
    void multiplierGrowsByTenPercentPerExtraMemberThroughEight() {
        for (int size = 1; size <= 8; size++) {
            assertEquals(1.0 + 0.1 * (size - 1), TeamScaling.multiplier(size), 1e-9, "team of " + size);
        }
    }

    @Test
    void soloPlayGainsExactlyWhatTheMemberEarned() {
        assertEquals(100.0, TeamScaling.teamPoolGain(100.0, 1), 1e-9);
    }

    @Test
    void teamsOutpaceSoloOnlySlightlyWhenEveryMemberMinesTheSameAmount() {
        // Each member earns 100; a team of n therefore contributes 100 * n.
        double solo = TeamScaling.teamPoolGain(100.0, 1);
        for (int size = 1; size <= 8; size++) {
            double pooled = TeamScaling.teamPoolGain(100.0 * size, size);
            assertEquals(100.0 * TeamScaling.multiplier(size), pooled, 1e-9, "team of " + size);
            // The bug this replaced let a team of five progress 19x as fast.
            assertTrue(pooled / solo <= 1.7 + 1e-9, "team of " + size + " outpaces solo by " + (pooled / solo) + "x");
        }
    }

    @Test
    void anIdleMemberDilutesTheSharedPool() {
        // Two members, only one mining: the pool gets half the gain, plus the 1.1x bonus.
        assertEquals(55.0, TeamScaling.teamPoolGain(100.0, 2), 1e-9);
    }

    @Test
    void rejectsEmptyTeams() {
        assertThrows(IllegalArgumentException.class, () -> TeamScaling.multiplier(0));
        assertThrows(IllegalArgumentException.class, () -> TeamScaling.teamPoolGain(100.0, -1));
    }
}
