package com.orevault.orevault.worldgen;

import com.google.common.collect.ImmutableList;
import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Dynamic per-team Vault dimension registration (design spec section 3.1). Dimensions are
 * created on first portal activation, re-created on reset, and deleted when a team disbands.
 * NeoForge 26.1 pattern: unfreeze the LEVEL_STEM registry, register the stem, re-freeze,
 * then construct the ServerLevel exactly as vanilla does in
 * {@link MinecraftServer#createLevels()} and publish it via {@code forgeGetWorldMap()}.
 */
public final class VaultDimensions {
    public static final ResourceKey<DimensionType> VAULT_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, OreVault.id("ore_vault"));
    public static final ResourceKey<DimensionType> VAULT_TYPE_EXPANDED =
            ResourceKey.create(Registries.DIMENSION_TYPE, OreVault.id("ore_vault_expanded"));

    private VaultDimensions() {
    }

    /** Gets or creates the team's Vault level. Safe to call at any time. */
    public static ServerLevel ensureVault(MinecraftServer server, UUID teamId) {
        ResourceKey<Level> dimKey = TeamHelper.vaultDimensionKey(teamId);
        ServerLevel existing = server.getLevel(dimKey);
        if (existing != null) {
            return existing;
        }
        OreVaultTeamData data = OreVaultTeamData.get(server, teamId);
        boolean expanded = data.vaultExpanded() && data.hasNode("vault_expansion");
        ServerLevel level = createLevel(server, teamId, expanded);
        if (level != null) {
            data.setVaultCreated(true);
        }
        return level;
    }

    /** Registers the LevelStem and creates the ServerLevel, mirroring vanilla createLevels(). */
    private static ServerLevel createLevel(MinecraftServer server, UUID teamId, boolean expanded) {
        ResourceKey<LevelStem> stemKey = TeamHelper.vaultStemKey(teamId);
        ResourceKey<Level> dimKey = TeamHelper.vaultDimensionKey(teamId);

        Holder<DimensionType> type = BuiltInRegistries.DIMENSION_TYPE.getHolder(
                expanded ? VAULT_TYPE_EXPANDED : VAULT_TYPE).orElse(null);
        if (type == null) {
            OreVault.LOGGER.error("Ore Vault: dimension type for team {} missing (expanded={}) — is the datapack loaded?",
                    teamId, expanded);
            return null;
        }

        VaultChunkGenerator generator = new VaultChunkGenerator(teamId, expanded);
        LevelStem stem = new LevelStem(type, generator);

        Registry<LevelStem> stemRegistry = levelStemRegistry(server);
        if (stemRegistry instanceof net.minecraft.core.MappedRegistry<LevelStem> mapped) {
            mapped.unfreeze(false);
        }
        try {
            if (stemRegistry.containsKey(stemKey)) {
                stemRegistry.unbind(stemKey);
            }
            net.minecraft.core.Registry.register(stemRegistry, stemKey, stem);
        } finally {
            if (stemRegistry instanceof net.minecraft.core.MappedRegistry<LevelStem> mapped) {
                mapped.freeze();
            }
        }

        ServerLevel overworld = server.overworld();
        DerivedLevelData levelData = new DerivedLevelData(server.getWorldData(), overworld.getLevelData());
        long seed = server.getWorldGenSettings().options().seed();
        ServerLevel level = new ServerLevel(
                server,
                server.executor,
                server.storageSource,
                levelData,
                dimKey,
                stem,
                server.getWorldData().isDebugWorld(),
                BiomeManager.obfuscateSeed(seed),
                ImmutableList.of(),
                false
        );
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        server.forgeGetWorldMap().put(dimKey, level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        OreVault.LOGGER.info("Ore Vault: created Vault dimension {} for team {}", dimKey.location(), teamId);
        return level;
    }

    @SuppressWarnings("unchecked")
    private static Registry<LevelStem> levelStemRegistry(MinecraftServer server) {
        return (Registry<LevelStem>) server.registries.compositeAccess().lookupOrThrow(Registries.LEVEL_STEM);
    }

    /**
     * Deletes the team's Vault: unloads the level, removes the registry entry, deletes all
     * chunk data. Optionally backs up region files first (design spec section 3.5).
     */
    public static boolean deleteVault(MinecraftServer server, UUID teamId, boolean backup) {
        ResourceKey<Level> dimKey = TeamHelper.vaultDimensionKey(teamId);
        ServerLevel level = server.getLevel(dimKey);

        if (backup && level != null) {
            backupVault(server, teamId, level);
        }
        if (level != null) {
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
            server.forgeGetWorldMap().remove(dimKey);
            try {
                level.close();
            } catch (IOException e) {
                OreVault.LOGGER.error("Ore Vault: failed to close Vault level for team {}", teamId, e);
            }
        }

        ResourceKey<LevelStem> stemKey = TeamHelper.vaultStemKey(teamId);
        Registry<LevelStem> stemRegistry = levelStemRegistry(server);
        if (stemRegistry instanceof net.minecraft.core.MappedRegistry<LevelStem> mapped) {
            mapped.unfreeze(false);
        }
        try {
            if (stemRegistry.containsKey(stemKey)) {
                stemRegistry.unbind(stemKey);
            }
        } finally {
            if (stemRegistry instanceof net.minecraft.core.MappedRegistry<LevelStem> mapped) {
                mapped.freeze();
            }
        }

        // Delete dimension storage folder.
        Path dimPath = server.storageSource.getDimensionPath(dimKey);
        try (Stream<Path> paths = Files.walk(dimPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException e) {
            OreVault.LOGGER.error("Ore Vault: failed to delete Vault storage for team {}", teamId, e);
        }
        OreVault.LOGGER.info("Ore Vault: deleted Vault dimension for team {} ({})",
                teamId, dimPath.toAbsolutePath());
        return true;
    }

    /** Copies region data to world/orevault_backups/&lt;teamId&gt;/&lt;timestamp&gt;/ before deletion. */
    public static void backupVault(MinecraftServer server, UUID teamId, ServerLevel level) {
        Path source = server.storageSource.getDimensionPath(level.dimension());
        Path backupDir = server.storageSource.getWorldDir()
                .resolve("orevault_backups")
                .resolve(TeamHelper.teamIdNoHyphens(teamId))
                .resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
        try {
            copyTree(source, backupDir);
            OreVault.LOGGER.info("Ore Vault: backed up Vault {} to {}", teamId, backupDir.toAbsolutePath());
        } catch (IOException e) {
            OreVault.LOGGER.error("Ore Vault: backup of Vault {} failed", teamId, e);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(source)) {
            paths.forEach(p -> {
                try {
                    Path dest = target.resolve(source.relativize(p).toString());
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(p, dest);
                    }
                } catch (IOException ignored) {
                }
            });
        }
    }

    /** Reset: delete + recreate fresh (design spec section 3.5). Preserves all progression. */
    public static boolean resetVault(MinecraftServer server, UUID teamId, boolean backup) {
        deleteVault(server, teamId, backup);
        return ensureVault(server, teamId) != null;
    }

    /** Called on server start: registers levels for all teams whose Vault exists. */
    public static void onServerStarted(MinecraftServer server) {
        if (!TeamHelper.managerLoaded()) {
            return;
        }
        for (var team : TeamHelper.manager().getTeams()) {
            OreVaultTeamData data = server.getDataStorage()
                    .computeIfAbsent(OreVaultTeamData.type(team.getTeamId()));
            if (data.vaultCreated()) {
                ensureVault(server, team.getTeamId());
            }
        }
    }
}
