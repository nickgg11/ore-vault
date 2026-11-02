package com.orevault.orevault.data;

import com.mojang.serialization.Codec;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.key.VaultKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One team's saved data, in the server's storage (§11 SavedData Pattern, ADR 0005). Main thread only: the generator reads a
 * snapshot, never this (rule 1).
 *
 * <p>A read goes through {@link #read} and creates nothing. Only a write goes through {@code forWrite}, so a player who logs
 * in and never mines has no file (S18). {@code forWrite} is also what keeps a damaged file's bytes: the storage reports an
 * unreadable file and an absent one alike, as null, and {@code computeIfAbsent} would then write an empty team over it.
 */
public final class OreVaultTeamData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(OreVaultTeamData.class);

    private final UUID teamId;
    private TeamFields fields;

    private OreVaultTeamData(UUID teamId, TeamFields fields) {
        this.teamId = teamId;
        this.fields = fields;
    }

    /** The codec reads through a hand-written reader that cannot fail, as ADR 0005 section 6 requires. */
    public static Codec<OreVaultTeamData> codec(UUID teamId) {
        return CompoundTag.CODEC.xmap(
                tag -> new OreVaultTeamData(teamId, TeamFields.read(tag, teamId, LOGGER::warn)),
                data -> data.fields.write(data.teamId));
    }

    public static SavedDataType<OreVaultTeamData> type(UUID teamId) {
        Identifier id = Identifier.fromNamespaceAndPath(OreVault.MOD_ID, VaultKeys.teamDataPath(teamId));
        return new SavedDataType<>(id, () -> new OreVaultTeamData(teamId, TeamFields.DEFAULTS), codec(teamId));
    }

    /** The team's fields, every one at its default for a team with no data. Writes nothing. */
    public static TeamFields read(MinecraftServer server, UUID teamId) {
        OreVaultTeamData data = server.getDataStorage().get(type(teamId));
        return data == null ? TeamFields.DEFAULTS : data.fields;
    }

    /** Whether the team has ever written anything, a file that cannot be read included. */
    public static boolean hasRecord(MinecraftServer server, UUID teamId) {
        return server.getDataStorage().get(type(teamId)) != null || Files.exists(DataPaths.teamFile(server, teamId));
    }

    /**
     * The team's data to change, created if it has none. The one write accessor (ADR 0005 section 2). If the file cannot be
     * read, its bytes are copied aside first; play goes on with a fresh team. A copy that fails leaves the file alone: the
     * caller gets data that is never saved, so this write is lost and the next one tries the copy again.
     */
    public static OreVaultTeamData forWrite(MinecraftServer server, UUID teamId) {
        SavedDataStorage storage = server.getDataStorage();
        SavedDataType<OreVaultTeamData> type = type(teamId);
        OreVaultTeamData data = storage.get(type);
        if (data != null) {
            return data;
        }
        Path file = DataPaths.teamFile(server, teamId);
        if (Files.exists(file) && !copyAside(file, server.overworld().getGameTime())) {
            return new OreVaultTeamData(teamId, TeamFields.DEFAULTS);
        }
        return storage.computeIfAbsent(type);
    }

    /**
     * For a write a player caused. A fake player is refused before anything happens: FTB Teams has no team for one, and
     * {@code TeamHelper} answers its id with itself, so {@code teamId} would be a phantom (§3.2).
     */
    public static Optional<OreVaultTeamData> forWrite(ServerPlayer player, UUID teamId) {
        if (player instanceof FakePlayer) {
            return Optional.empty();
        }
        return Optional.of(forWrite(player.level().getServer(), teamId));
    }

    private static boolean copyAside(Path file, long gameTime) {
        Path copy = DataPaths.unreadableCopy(file, gameTime);
        try {
            Files.copy(file, copy);
            LOGGER.error("Team file {} cannot be read; its bytes are kept at {} and the team starts fresh", file, copy);
            return true;
        } catch (IOException e) {
            LOGGER.error("Team file {} cannot be read or copied to {}; leaving it alone", file, copy, e);
            return false;
        }
    }

    public TeamFields fields() {
        return fields;
    }

    /** @param gameTime overworld game time, the one clock every saved time uses (ADR 0005 section 1) */
    public void markDisbanded(long gameTime) {
        fields = fields.withDisbandedAt(gameTime);
        setDirty();
    }
}
