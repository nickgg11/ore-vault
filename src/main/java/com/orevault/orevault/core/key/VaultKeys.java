package com.orevault.orevault.core.key;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The names a team's Vault and data are saved under (ADR 0002, ADR 0005 section 2). They are save data: player files
 * store the dimension key and the team file is found by name, so none of these changes without a migration. A pure class
 * so any adapter can tell whose a level is without importing {@code vault}.
 */
public final class VaultKeys {
    /** The Vault's type, at the top of its folder (ADR 0005 section 3a). */
    public static final String VAULT_TYPE_FILE = "orevault_vault.dat";

    /**
     * The layer stack this build generates, which the type file's {@code layout} and the player's {@code in_vault} record
     * (ADR 0009, amended 2026-11-21). 1 is the stack of 2026-10, stone to Y=245; 2 is the surface at Y=100. It moves only
     * with a code change to the built-in stack, never with a datapack edit, and every move comes with the start-of-server
     * pass's archive of the older folders (ADR 0007 step 4).
     */
    public static final int CURRENT_LAYOUT = 2;
    /** What a record written before {@code layout} existed holds: every such record was written under layout 1. */
    public static final int FIRST_LAYOUT = 1;

    private static final String VAULT_PREFIX = "vault_";
    private static final String TEAM_PREFIX = "team_";
    private static final Pattern VAULT_FOLDER = Pattern.compile("vault_[0-9a-f]{32}");

    private VaultKeys() {
    }

    /** {@code vault_<teamUuidNoHyphens>}, lower-case hex: the path of the dimension key and the folder's name. */
    public static String dimensionPath(UUID teamId) {
        return VAULT_PREFIX + hex(teamId);
    }

    /** {@code team_<teamUuidNoHyphens>}: the path of the team's {@code SavedData} id. */
    public static String teamDataPath(UUID teamId) {
        return TEAM_PREFIX + hex(teamId);
    }

    /**
     * The team a Vault folder belongs to, or nothing for a name that is not ours. A name this matches always parses. The
     * match is the whole name, so {@code vault_<hex>.deleting}, a leftover of ADR 0007 step 8, is not a Vault.
     */
    public static Optional<UUID> teamIdFromDirectoryName(String name) {
        if (!VAULT_FOLDER.matcher(name).matches()) {
            return Optional.empty();
        }
        String hex = name.substring(VAULT_PREFIX.length());
        return Optional.of(UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20)));
    }

    private static String hex(UUID teamId) {
        return teamId.toString().replace("-", "");
    }
}
