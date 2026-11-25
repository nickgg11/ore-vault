package com.orevault.orevault.core.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** §4.2's team pool scaling. */
class TeamScalingTest {
    @Test
    void theRateAgainstSoloIsTheSpecTable() {
        assertEquals(1.0, TeamScaling.rateVsSolo(1), 1e-12);
        assertEquals(1.1, TeamScaling.rateVsSolo(2), 1e-12);
        assertEquals(1.2, TeamScaling.rateVsSolo(3), 1e-12);
        assertEquals(1.4, TeamScaling.rateVsSolo(5), 1e-12);
    }

    @Test
    void sizeZeroOrUnknownReadsAsOne() {
        assertEquals(1.0, TeamScaling.rateVsSolo(0), 1e-12);
        assertEquals(1.0, TeamScaling.rateVsSolo(-3), 1e-12);
        assertEquals(10_000, TeamScaling.poolGainThousandths(10_000, 0));
    }

    @Test
    void tenResonanceOnATeamOfFiveAddsTwoPointEight() {
        assertEquals(2_800, TeamScaling.poolGainThousandths(10_000, 5));
    }

    @Test
    void soloAddsTheGainUnchanged() {
        assertEquals(12_500, TeamScaling.poolGainThousandths(12_500, 1));
        assertEquals(50, TeamScaling.poolGainThousandths(50, 1));
    }

    @Test
    void fiveMembersEachPickingUpTenFillThePoolOnePointFourTimesAsFastAsOne() {
        long team = 5 * TeamScaling.poolGainThousandths(10_000, 5);
        long solo = TeamScaling.poolGainThousandths(10_000, 1);
        assertEquals(1.4, (double) team / solo, 1e-12);
    }

    @Test
    void anAmountIsRoundedOnceHalfUp() {
        // 50 * 12 / 30 = 20 exactly; 1 * 11 / 20 = 0.55, up to 1; 1 * 12 / 30 = 0.4, down to 0.
        assertEquals(20, TeamScaling.poolGainThousandths(50, 3));
        assertEquals(1, TeamScaling.poolGainThousandths(1, 2));
        assertEquals(0, TeamScaling.poolGainThousandths(1, 3));
        // 5 * 12 / 30 = 2 exactly; 25 * 11 / 20 = 13.75, up to 14; 30 * 11 / 20 = 16.5, half up to 17.
        assertEquals(2, TeamScaling.poolGainThousandths(5, 3));
        assertEquals(14, TeamScaling.poolGainThousandths(25, 2));
        assertEquals(17, TeamScaling.poolGainThousandths(30, 2));
    }

    @Test
    void aNegativeGainIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> TeamScaling.poolGainThousandths(-1, 2));
    }
}
