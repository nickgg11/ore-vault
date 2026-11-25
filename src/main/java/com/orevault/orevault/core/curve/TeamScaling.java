package com.orevault.orevault.core.curve;

import com.orevault.orevault.core.team.TeamSize;

/**
 * Team pool scaling (§4.2): the pool takes the sum of member gains, divided by team size, times a coordination bonus of
 * 0.1 a member past the first. A five-person team progresses 1.4 times as fast as a solo player, not five times. The curve
 * is priced for a solo player, so no assumed team size appears anywhere.
 *
 * <p>The size is every member of the FTB team, online or offline ({@code TeamHelper#size}), floored at 1 by
 * {@link TeamSize}: an unknown or emptied team is a team of one, never a divisor of 0.
 */
public final class TeamScaling {
    /** The coordination bonus is 0.1 a member past the first (§4.2), held as tenths so the sums stay integers. */
    private static final long TENTHS = 10;
    private static final long BONUS_TENTHS_PER_EXTRA_MEMBER = 1;

    private TeamScaling() {
    }

    /** {@code 1 + 0.1 * (size - 1)}, in tenths. */
    private static long rateTenths(long size) {
        return TENTHS + BONUS_TENTHS_PER_EXTRA_MEMBER * (size - 1);
    }

    /** How fast a team of this size fills its pool against a solo player: 1.0, 1.1, 1.2 and 1.4 at 1, 2, 3 and 5. */
    public static double rateVsSolo(int teamSize) {
        return rateTenths(TeamSize.of(teamSize)) / (double) TENTHS;
    }

    /**
     * What one member's gain adds to the pool, in thousandths: {@code gain / size * (1 + 0.1 * (size - 1))}, which is
     * {@code gain * (10 + (size - 1)) / (10 * size)} in tenths. Computed in integers and rounded once, half up, where the
     * amount enters the pool (ADR 0005 section 3), so 10 Resonance on a team of five is exactly 2.8.
     *
     * @param gainThousandths the member's gain before scaling, never negative
     */
    public static long poolGainThousandths(long gainThousandths, int teamSize) {
        if (gainThousandths < 0) {
            throw new IllegalArgumentException("A gain of " + gainThousandths + " thousandths");
        }
        long size = TeamSize.of(teamSize);
        long numerator = Math.multiplyExact(gainThousandths, rateTenths(size));
        long denominator = TENTHS * size;
        return Math.addExact(Math.multiplyExact(numerator, 2L), denominator) / (2 * denominator);
    }
}
