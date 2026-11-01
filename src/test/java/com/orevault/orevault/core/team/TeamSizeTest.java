package com.orevault.orevault.core.team;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A team is never smaller than one (§2, §9). */
class TeamSizeTest {
    @Test
    void noMembersIsATeamOfOne() {
        assertEquals(1, TeamSize.of(0));
    }

    @Test
    void oneMemberIsATeamOfOne() {
        assertEquals(1, TeamSize.of(1));
    }

    @Test
    void aPartyIsItsMemberCount() {
        assertEquals(7, TeamSize.of(7));
    }
}
