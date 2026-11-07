package com.orevault.orevault.vault;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.GenSnapshot;
import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.VaultVariant;
import com.orevault.orevault.core.key.VaultKeys;
import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.GenSnapshots;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.VaultTypeFile;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and restores team Vaults (§3.1, §11 Dimension-per-Team Registration, ADR 0002). Main thread only.
 *
 * <p>Creation is lazy: {@link #findOrCreate} is the only way a new Vault comes to exist, and only the portal path and the
 * S44 debug command call it (ArchUnit named rule 7). FTB Teams makes a team for every login, so a Vault per team would be
 * a {@code ServerLevel} per account ever seen. Restoration is eager: {@link #restoreAll} registers every Vault on disk at
 * server start, before anyone connects, because a player may have logged out inside one. The last build lost a player to
 * the gap between the two (old #89: the restore was removed with the lazy-creation fix, and the player came back at Vault
 * height in the Overworld sky).
 *
 * <p>The maps are static and a personal team's id is the same in a second world in one client, so the root listener
 * clears them at {@code ServerStoppedEvent}. Nothing removes a level while the server runs (S19).
 */
public final class VaultDimensions {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultDimensions.class);
    /** The type each Vault registered this session was made under. The key follows from the team id, so it is not kept. */
    private static final Map<UUID, VaultVariant> VARIANTS = new HashMap<>();

    /**
     * What restore did with one team id.
     *
     * @param reason why it was skipped, or empty when it was restored
     */
    public record Restore(UUID teamId, boolean restored, String reason) {
    }

    private VaultDimensions() {
    }

    /** {@code orevault:vault_<teamUuidNoHyphens>}, save data from the first release (ADR 0002). */
    public static ResourceKey<Level> dimensionKey(UUID teamId) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(OreVault.MOD_ID, VaultKeys.dimensionPath(teamId)));
    }

    /** The team whose Vault a level is, read from its key, so it answers for a Vault with no entry here. */
    public static Optional<UUID> teamOf(ResourceKey<Level> level) {
        Identifier id = level.identifier();
        return OreVault.MOD_ID.equals(id.getNamespace()) ? VaultKeys.teamIdFromDirectoryName(id.getPath()) : Optional.empty();
    }

    /** The variant a Vault registered this session was made under. */
    public static Optional<VaultVariant> variant(UUID teamId) {
        return Optional.ofNullable(VARIANTS.get(teamId));
    }

    /**
     * The portal path (§3.2): the team's Vault, made if it has never been entered. Empty when the Vault's folder holds no
     * readable type: that is logged at ERROR naming the file, and the caller tells the player.
     */
    public static Optional<ServerLevel> findOrCreate(MinecraftServer server, UUID teamId) {
        return ensureTeamDimension(server, teamId, () -> variantForANewVault(server, teamId));
    }

    /**
     * As {@link #findOrCreate(MinecraftServer, UUID)}, with the type a new Vault gets named: the S44 debug command. A Vault
     * whose folder exists keeps the type in its file whatever is asked.
     */
    public static Optional<ServerLevel> findOrCreate(MinecraftServer server, UUID teamId, VaultVariant forANewVault) {
        return ensureTeamDimension(server, teamId, () -> forANewVault);
    }

    /**
     * §3.5 step 5: expanded once the team owns Vault Expansion. The tree is not built yet (E11-S7, #290), so every new Vault
     * is base. Asked only when there is no folder.
     */
    private static VaultVariant variantForANewVault(MinecraftServer server, UUID teamId) {
        return VaultVariant.BASE;
    }

    /**
     * ADR 0002's restore, from the root {@code ServerStartedEvent} listener. One team that fails never stops the others.
     *
     * @param resetTeamIds the resets ADR 0007's start-of-server pass finished at this start, restored whether or not a folder
     *     exists; none until that pass exists (E11)
     */
    public static List<Restore> restoreAll(MinecraftServer server, Collection<UUID> resetTeamIds) {
        Set<UUID> teamIds = new LinkedHashSet<>(foldersOnDisk(server));
        teamIds.addAll(resetTeamIds);
        List<Restore> results = new ArrayList<>();
        for (UUID teamId : teamIds) {
            try {
                results.add(restoreOne(server, teamId));
            } catch (RuntimeException e) {
                LOGGER.error("Restoring the Vault of team {} failed", teamId, e);
                results.add(new Restore(teamId, false, "failed: " + e));
            }
        }
        long restored = results.stream().filter(Restore::restored).count();
        LOGGER.info("Restored {} Vaults, skipped {}", restored, results.size() - restored);
        results.stream().filter(result -> !result.restored())
                .forEach(result -> LOGGER.info("Skipped the Vault of team {}: {}", result.teamId(), result.reason()));
        return results;
    }

    /** Every directory under {@code <world>/dimensions/orevault/} whose name is ours; any other is logged and skipped. */
    private static List<UUID> foldersOnDisk(MinecraftServer server) {
        Path root = DataPaths.vaultsFolder(server);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<UUID> found = new ArrayList<>();
        try (Stream<Path> entries = Files.list(root)) {
            for (Path entry : entries.filter(Files::isDirectory).toList()) {
                String name = entry.getFileName().toString();
                VaultKeys.teamIdFromDirectoryName(name).ifPresentOrElse(found::add,
                        () -> LOGGER.warn("Folder {} is not a Vault of this mod; skipped", entry));
            }
        } catch (IOException | UncheckedIOException e) {
            LOGGER.error("Cannot list {}; no Vault is restored from it", root, e);
        }
        return found;
    }

    /**
     * Restore steps 2 to 6 for one team. Team data is read for {@code disbanded_at} only, and an absent or unreadable team
     * file never stops a Vault, whose type is in its own folder.
     */
    public static Restore restoreOne(MinecraftServer server, UUID teamId) {
        if (OreVaultTeamData.isUnreadable(server, teamId)) {
            LOGGER.error("Team file {} cannot be read. Its Vault is restored under its own type; the team's progress in that "
                    + "file is lost to play until it is repaired by hand", DataPaths.teamFile(server, teamId));
        } else if (OreVaultTeamData.read(server, teamId).disbandedAt().isPresent()) {
            return new Restore(teamId, false, "the team has disbanded");
        }
        return ensureTeamDimension(server, teamId, () -> variantForANewVault(server, teamId)).isPresent()
                ? new Restore(teamId, true, "")
                : new Restore(teamId, false, "its type file is missing, damaged or names no known type; the ERROR above names it");
    }

    /** ADR 0002's creation steps, shared by the portal path and restore. */
    private static Optional<ServerLevel> ensureTeamDimension(MinecraftServer server, UUID teamId, Supplier<VaultVariant> forANewVault) {
        ResourceKey<Level> key = dimensionKey(teamId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            return Optional.of(existing);
        }
        Optional<VaultVariant> type = VaultTypeFile.openOrCreate(
                DataPaths.vaultFolder(server, teamId), forANewVault, LOGGER::warn, LOGGER::error);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        VaultVariant variant = type.get();
        Supplier<GenSnapshot> snapshots = GenSnapshots.fill(server, teamId, variant);
        Holder.Reference<DimensionType> dimensionType = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(variant.id())));
        Holder.Reference<Biome> biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(VaultChunkGenerator.BIOME);
        LayerStack layers = VaultLayerConfig.load(server, variant);
        VaultChunkGenerator generator = new VaultChunkGenerator(teamId, biome, variant, layers, snapshots);
        LevelStem stem = new LevelStem(dimensionType, generator);
        registerStem(server, ResourceKey.create(Registries.LEVEL_STEM, key.identifier()), stem);

        DerivedLevelData levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
        // storageSource is protected with no getter; the access transformer line names this ticket (#237).
        ServerLevel level = new ServerLevel(server, Util.backgroundExecutor(), server.storageSource, levelData, key, stem,
                false, BiomeManager.obfuscateSeed(server.overworld().getSeed()), List.of(), false);
        VaultLevels.register(server, key, level);
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        reactivateTickets(level);

        VARIANTS.put(teamId, variant);
        LOGGER.info("Registered Vault {} ({}, Y={}..{}) for team {} at server tick {}", key.identifier(), variant.id(),
                variant.minY(), variant.minY() + variant.height() - 1, teamId, server.getTickCount());
        return Optional.of(level);
    }

    /**
     * Creation step 7. A key already present is not an error: a registry entry cannot be removed, so it is still there on
     * the re-registration after a reset, and the level is built from the fresh stem it is handed. The first build froze
     * only after a register that returned, so a throw left the live registry unfrozen.
     */
    private static void registerStem(MinecraftServer server, ResourceKey<LevelStem> stemKey, LevelStem stem) {
        Registry<LevelStem> registry = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        if (!(registry instanceof MappedRegistry<LevelStem> stems)) {
            throw new IllegalStateException("The level stem registry is a " + registry.getClass().getName() + "; cannot add "
                    + stemKey.identifier());
        }
        if (stems.containsKey(stemKey)) {
            return;
        }
        stems.unfreeze(false);
        try {
            stems.register(stemKey, stem, RegistrationInfo.BUILT_IN);
        } finally {
            stems.freeze();
        }
    }

    /**
     * Creation step 13, as {@code MinecraftServer#prepareLevels} does for the levels it sees at start: a restored Vault is
     * not among them, so without this its forced chunks never come back. A new Vault has no ticket file and nothing happens.
     * {@code ForcedChunkManager.activateAllDeactivatedTickets} is {@code @ApiStatus.Internal} with no supported alternative
     * (ADR 0002), the same footing as the world map; it runs each ticket controller's validation itself.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static void reactivateTickets(ServerLevel level) {
        TicketStorage saved = level.getDataStorage().get(TicketStorage.TYPE);
        if (saved != null) {
            saved.activateAllDeactivatedTickets();
            ForcedChunkManager.activateAllDeactivatedTickets(level, saved);
        }
    }

    /** At {@code ServerStoppedEvent}: the next world in this client starts with none of this one's entries. */
    public static void clear() {
        VARIANTS.clear();
    }
}
