package com.orevault.orevault.core.session;

import com.orevault.orevault.core.key.VaultKeys;
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
 *
 * <p>Before case 1, a player whose {@code in_vault} was written under an older layout is sent home with the archive's
 * message (ADR 0002 restore step 6; ADR 0007 pass step 4; §11). The archive changes neither the dimension key nor the
 * team's generation, so after a teammate has re-entered, the fresh Vault is loaded under the same key and the other cases
 * would keep the player at a position in the old terrain, up to 150 blocks in the air or inside stone. The layout is read
 * from the player's own record, so the message reaches them at any later login, not only in the session of the archive.
 */
public final class LoginCheck {
    /** What to do with a player who has just logged in. */
    public enum Outcome {
        /** Case 4. */
        STAY,
        /** Case 1: the Vault {@code in_vault} names is not the level they are in. No chat line; the log says why. */
        SEND_HOME,
        /** Before case 1: {@code in_vault} was written under an older layout, so that Vault was archived (§11). */
        SEND_HOME_OLDER_LAYOUT,
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
     * @param inVaultLayout the layout {@code in_vault} was written under; read only when {@code inVaultTeam} is set
     */
    public record Facts(@Nullable UUID inVaultTeam, int inVaultGeneration, @Nullable UUID levelTeam,
            boolean levelTeamDisbanded, int levelTeamGeneration, UUID playerTeam, int inVaultLayout) {
        /** Facts whose {@code in_vault}, if any, was written under the current layout. */
        public Facts(@Nullable UUID inVaultTeam, int inVaultGeneration, @Nullable UUID levelTeam,
                boolean levelTeamDisbanded, int levelTeamGeneration, UUID playerTeam) {
            this(inVaultTeam, inVaultGeneration, levelTeam, levelTeamDisbanded, levelTeamGeneration, playerTeam,
                    VaultKeys.CURRENT_LAYOUT);
        }
    }

    private LoginCheck() {
    }

    public static Outcome decide(Facts facts) {
        if (facts.inVaultTeam() != null && facts.inVaultLayout() < VaultKeys.CURRENT_LAYOUT) {
            return Outcome.SEND_HOME_OLDER_LAYOUT;
        }
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
