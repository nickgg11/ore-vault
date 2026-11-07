package com.orevault.orevault.core.session;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * ADR 0002's {@code in_vault} login check (§11 Dimension-per-Team Registration), as a function of plain facts so each case
 * runs in JUnit. It is the net under every restore failure: a Vault that was skipped, archived or failed to load costs the
 * player a trip home, never a fall from Vault height in the Overworld (old #89).
 *
 * <p>The cases are tried in ADR 0002's order. A player in a Vault with no {@code in_vault} (nothing recorded their entry) is
 * still checked for cases 2, but never moved by case 3, which needs the generation they entered under.
 */
public final class LoginCheck {
    /** What to do with a player who has just logged in. */
    public enum Outcome {
        /** Case 4. */
        STAY,
        /** Case 1: the Vault {@code in_vault} names is not the level they are in. No chat line; the log says why. */
        SEND_HOME,
        /** Case 2: their Vault's team has disbanded. */
        SEND_HOME_DISBANDED,
        /** Case 2: their Vault's team is not their team (they left it while offline, or an operator put them there). */
        SEND_HOME_NOT_THEIR_TEAM,
        /** Case 3: the Vault was reset while they were offline inside; they go to the team anchor (X=0, Z=0). */
        TO_ANCHOR
    }

    /**
     * @param inVaultTeam the team {@code in_vault} names, or null when it is not set
     * @param inVaultGeneration that team's {@code vault_generation} at entry; read only when {@code inVaultTeam} is set
     * @param levelTeam the team whose Vault the player is standing in, or null when the level is not a Vault
     * @param levelTeamDisbanded whether that team has {@code disbanded_at} set
     * @param levelTeamGeneration that team's current {@code vault_generation}
     * @param playerTeam the player's current effective team
     */
    public record Facts(@Nullable UUID inVaultTeam, int inVaultGeneration, @Nullable UUID levelTeam,
            boolean levelTeamDisbanded, int levelTeamGeneration, UUID playerTeam) {
    }

    private LoginCheck() {
    }

    public static Outcome decide(Facts facts) {
        if (facts.inVaultTeam() != null && !Objects.equals(facts.inVaultTeam(), facts.levelTeam())) {
            return Outcome.SEND_HOME;
        }
        if (facts.levelTeam() == null) {
            return Outcome.STAY;
        }
        if (facts.levelTeamDisbanded()) {
            return Outcome.SEND_HOME_DISBANDED;
        }
        if (!facts.levelTeam().equals(facts.playerTeam())) {
            return Outcome.SEND_HOME_NOT_THEIR_TEAM;
        }
        if (facts.inVaultTeam() != null && facts.levelTeamGeneration() > facts.inVaultGeneration()) {
            return Outcome.TO_ANCHOR;
        }
        return Outcome.STAY;
    }
}
