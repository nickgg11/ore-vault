package com.orevault.orevault.data;

import com.orevault.orevault.core.key.VaultKeys;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where a team's files are (ADR 0005 section 2), in one place, so the write accessor, restore and ADR 0007's raw read
 * never each rebuild them. {@code SavedDataStorage#getDataFile} is private, so the team file is built from public parts
 * the same way it does.
 */
public final class DataPaths {
    private static final String NAMESPACE_FOLDER = "orevault";
    private static final String DIMENSIONS_FOLDER = "dimensions";
    private static final String SAVED_DATA_SUFFIX = ".dat";
    private static final String UNREADABLE_SUFFIX = ".unreadable-";

    private DataPaths() {
    }

    public static Path teamFile(MinecraftServer server, UUID teamId) {
        return teamFile(server.getWorldPath(LevelResource.DATA), teamId);
    }

    public static Path vaultFolder(MinecraftServer server, UUID teamId) {
        return vaultFolder(server.getWorldPath(LevelResource.ROOT), teamId);
    }

    public static Path vaultTypeFile(MinecraftServer server, UUID teamId) {
        return vaultTypeFile(server.getWorldPath(LevelResource.ROOT), teamId);
    }

    static Path teamFile(Path dataFolder, UUID teamId) {
        return dataFolder.resolve(NAMESPACE_FOLDER).resolve(VaultKeys.teamDataPath(teamId) + SAVED_DATA_SUFFIX);
    }

    static Path vaultFolder(Path worldRoot, UUID teamId) {
        return worldRoot.resolve(DIMENSIONS_FOLDER).resolve(NAMESPACE_FOLDER).resolve(VaultKeys.dimensionPath(teamId));
    }

    static Path vaultTypeFile(Path worldRoot, UUID teamId) {
        return vaultFolder(worldRoot, teamId).resolve(VaultKeys.VAULT_TYPE_FILE);
    }

    /** Where an unreadable file's bytes are kept (ADR 0005 section 2). */
    static Path unreadableCopy(Path file, long gameTime) {
        return file.resolveSibling(file.getFileName() + UNREADABLE_SUFFIX + gameTime);
    }
}
