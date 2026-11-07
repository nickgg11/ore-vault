package com.orevault.orevault.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** These paths are where a player's world keeps its files (ADR 0005 section 2). */
class DataPathsTest {
    private static final UUID TEAM = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String HEX = "0123456789abcdef0123456789abcdef";

    @Test
    void theTeamFileIsUnderTheDataFolder() {
        assertEquals(Path.of("world/data/orevault/team_" + HEX + ".dat"), DataPaths.teamFile(Path.of("world/data"), TEAM));
    }

    @Test
    void restoreScansTheModsDimensionsFolder() {
        assertEquals(Path.of("world/dimensions/orevault"), DataPaths.vaultsFolder(Path.of("world")));
    }

    @Test
    void theVaultFolderIsUnderTheDimensionsFolder() {
        assertEquals(Path.of("world/dimensions/orevault/vault_" + HEX), DataPaths.vaultFolder(Path.of("world"), TEAM));
    }

    @Test
    void theTypeFileIsAtTheTopOfTheVaultFolder() {
        assertEquals(Path.of("world/dimensions/orevault/vault_" + HEX + "/orevault_vault.dat"),
                DataPaths.vaultTypeFile(Path.of("world"), TEAM));
    }

    @Test
    void theCopyAsideSitsBesideTheFileAndNamesTheGameTime() {
        Path file = Path.of("world/data/orevault/team_" + HEX + ".dat");
        assertEquals(Path.of("world/data/orevault/team_" + HEX + ".dat.unreadable-1234"), DataPaths.unreadableCopy(file, 1234L));
    }
}
