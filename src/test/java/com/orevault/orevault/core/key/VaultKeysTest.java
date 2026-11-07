package com.orevault.orevault.core.key;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The key and file names are save data (ADR 0002): these strings are what a player's world holds. */
class VaultKeysTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String HEX = "0123456789abcdef0123456789abcdef";

    @Test
    void theDimensionPathIsFrozen() {
        assertEquals("vault_" + HEX, VaultKeys.dimensionPath(TEAM));
    }

    @Test
    void theTeamDataPathIsFrozen() {
        assertEquals("team_" + HEX, VaultKeys.teamDataPath(TEAM));
    }

    @Test
    void theVaultTypeFileNameIsFrozen() {
        assertEquals("orevault_vault.dat", VaultKeys.VAULT_TYPE_FILE);
    }

    @Test
    void aDirectoryNameRoundTrips() {
        assertEquals(Optional.of(TEAM), VaultKeys.teamIdFromDirectoryName(VaultKeys.dimensionPath(TEAM)));
    }

    @Test
    void anAllZeroTeamKeepsItsLeadingZeros() {
        UUID team = new UUID(0L, 1L);
        assertEquals("vault_00000000000000000000000000000001", VaultKeys.dimensionPath(team));
        assertEquals(Optional.of(team), VaultKeys.teamIdFromDirectoryName(VaultKeys.dimensionPath(team)));
    }

    @Test
    void upperCaseHexIsRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName("vault_" + HEX.toUpperCase()));
    }

    @Test
    void thirtyOneDigitsAreRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName("vault_" + HEX.substring(1)));
    }

    @Test
    void thirtyThreeDigitsAreRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName("vault_" + HEX + "0"));
    }

    @Test
    void aHyphenatedNameIsRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName("vault_" + TEAM));
    }

    /** A leftover of ADR 0007 step 8 is the one name restore must never register. */
    @Test
    void aDeletingFolderIsRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName("vault_" + HEX + ".deleting"));
    }

    @Test
    void aNameWithoutThePrefixIsRejected() {
        assertEquals(Optional.empty(), VaultKeys.teamIdFromDirectoryName(HEX));
    }
}
