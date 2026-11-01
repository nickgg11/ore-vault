package com.orevault.orevault.core.team;

/**
 * The floor on a team's size. Every player is a team of at least themselves (§2), and the size is a divisor in team
 * scaling, so a 0 here threw on orb pickup in the first build. FTB Teams can really answer 0: a personal team whose
 * player has joined a party keeps its id and loses its member.
 */
public final class TeamSize {
    private static final int MINIMUM = 1;

    private TeamSize() {
    }

    public static int of(int members) {
        return Math.max(MINIMUM, members);
    }
}
