package com.orevault.orevault.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.core.session.LoginCheck.Facts;
import com.orevault.orevault.core.session.LoginCheck.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR 0002's login cases, in its order. */
class LoginCheckTest {
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void aPlayerOutsideAnyVaultWithNothingRecordedStays() {
        assertEquals(Outcome.STAY, LoginCheck.decide(new Facts(null, 0, null, false, 0, TEAM)));
    }

    /** Case 1, S14: the Vault was skipped at restore, so vanilla put them in the Overworld at Vault coordinates. */
    @Test
    void aPlayerWhoseVaultDidNotLoadIsSentHome() {
        assertEquals(Outcome.SEND_HOME, LoginCheck.decide(new Facts(TEAM, 0, null, false, 0, TEAM)));
    }

    @Test
    void aPlayerInADifferentVaultFromTheOneRecordedIsSentHome() {
        assertEquals(Outcome.SEND_HOME, LoginCheck.decide(new Facts(TEAM, 0, OTHER, false, 0, OTHER)));
    }

    /** Case 1 comes first: a recorded Vault that is gone wins over a disbanded one the player stands in. */
    @Test
    void caseOneIsTriedBeforeCaseTwo() {
        assertEquals(Outcome.SEND_HOME, LoginCheck.decide(new Facts(TEAM, 0, OTHER, true, 0, TEAM)));
    }

    @Test
    void aPlayerInADisbandedTeamsVaultIsSentHomeWithAReason() {
        assertEquals(Outcome.SEND_HOME_DISBANDED, LoginCheck.decide(new Facts(TEAM, 0, TEAM, true, 0, TEAM)));
    }

    /** RV-8: they left the team while offline. */
    @Test
    void aPlayerInAnotherTeamsVaultIsSentHomeWithAReason() {
        assertEquals(Outcome.SEND_HOME_NOT_THEIR_TEAM, LoginCheck.decide(new Facts(TEAM, 0, TEAM, false, 0, OTHER)));
    }

    @Test
    void caseTwoHoldsWithNothingRecorded() {
        assertEquals(Outcome.SEND_HOME_NOT_THEIR_TEAM, LoginCheck.decide(new Facts(null, 0, TEAM, false, 0, OTHER)));
        assertEquals(Outcome.SEND_HOME_DISBANDED, LoginCheck.decide(new Facts(null, 0, TEAM, true, 0, TEAM)));
    }

    @Test
    void aDisbandIsReportedBeforeATeamChange() {
        assertEquals(Outcome.SEND_HOME_DISBANDED, LoginCheck.decide(new Facts(TEAM, 0, TEAM, true, 0, OTHER)));
    }

    /** Case 3: a reset while they were offline inside. */
    @Test
    void aPlayerInAVaultResetSinceTheyEnteredGoesToTheAnchor() {
        assertEquals(Outcome.TO_ANCHOR, LoginCheck.decide(new Facts(TEAM, 1, TEAM, false, 2, TEAM)));
    }

    @Test
    void aPlayerInTheVaultTheyEnteredStays() {
        assertEquals(Outcome.STAY, LoginCheck.decide(new Facts(TEAM, 2, TEAM, false, 2, TEAM)));
    }

    /** An older generation on the team than in the record is no reset, and moving them would be a guess. */
    @Test
    void aGenerationBehindTheRecordedOneStays() {
        assertEquals(Outcome.STAY, LoginCheck.decide(new Facts(TEAM, 3, TEAM, false, 2, TEAM)));
    }

    @Test
    void aPlayerInTheirVaultWithNothingRecordedStays() {
        assertEquals(Outcome.STAY, LoginCheck.decide(new Facts(null, 0, TEAM, false, 5, TEAM)));
    }

    /** The AC's death case: respawned in the Overworld with in_vault cleared, the check moves nobody. */
    @Test
    void aPlayerRespawnedOutsideWithInVaultClearedStays() {
        assertEquals(Outcome.STAY, LoginCheck.decide(new Facts(null, 0, null, false, 0, TEAM)));
    }
}
