package com.orevault.orevault.skill;

/**
 * Team pool scaling for Resonance and Animus gain (§4.2, applied to both pools
 * per §5.2).
 *
 * <p>The team pool receives the <i>sum</i> of its members' gains divided by team
 * size, with a small coordination bonus on top:</p>
 *
 * <pre>teamPoolGain = sum(memberGains) / teamSize * (1 + 0.1 * (teamSize - 1))</pre>
 *
 * <p>So a five-person team progresses 40% faster than a solo player, not 500%
 * faster. Joining a team is meant to buy shared play and a shared tree, not a
 * shortcut through the curve — and because {@link LevelCurve} is calibrated for
 * a solo player, any multiplier much above 1 would invalidate it.</p>
 *
 * <p>The earlier {@code 1 + (teamSize - 1) * 0.7} step was both far too generous
 * and ambiguous: read as a multiplier applied on top of an already-summed pool,
 * a team of five would have progressed 19× as fast.</p>
 */
public final class TeamScaling {

    private TeamScaling() {
    }

    /**
     * Coordination multiplier for a team of {@code teamSize}: 1.0 solo, 1.1 duo,
     * 1.4 at five.
     *
     * @throws IllegalArgumentException if {@code teamSize} is below 1
     */
    public static double multiplier(int teamSize) {
        requireValidSize(teamSize);
        return 1.0 + NodeCosts.TEAM_SIZE_COORDINATION_STEP * (teamSize - 1);
    }

    /**
     * Amount to add to the team pool for a batch of member gains.
     *
     * @param summedMemberGains sum of the gains earned by individual members
     * @param teamSize          number of members on the team (not just those online)
     * @throws IllegalArgumentException if {@code teamSize} is below 1
     */
    public static double teamPoolGain(double summedMemberGains, int teamSize) {
        return summedMemberGains / teamSize * multiplier(teamSize);
    }

    private static void requireValidSize(int teamSize) {
        if (teamSize < 1) {
            throw new IllegalArgumentException("teamSize must be >= 1, got " + teamSize);
        }
    }
}
