package com.orevault.orevault.team;

import com.orevault.orevault.OreVault;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Thin wrapper over the FTB Teams API (hard dependency). Every Ore Vault system is keyed
 * on the FTB Team id.
 */
public final class TeamHelper {
    private TeamHelper() {
    }

    public static TeamManager manager() {
        return FTBTeamsAPI.api().getManager();
    }

    public static boolean managerLoaded() {
        return FTBTeamsAPI.api().isManagerLoaded();
    }

    public static Optional<Team> teamForPlayer(UUID playerId) {
        return managerLoaded() ? manager().getTeamForPlayerID(playerId) : Optional.empty();
    }

    public static Optional<Team> teamForPlayer(ServerPlayer player) {
        return teamForPlayer(player.getUUID());
    }

    public static Optional<Team> teamById(UUID teamId) {
        return managerLoaded() ? manager().getTeamByID(teamId) : Optional.empty();
    }

    public static Collection<ServerPlayer> onlineMembers(Team team) {
        return team.getOnlineMembers();
    }

    /** Team UUID without hyphens, used in dimension registry keys. */
    public static String teamIdNoHyphens(UUID teamId) {
        return teamId.toString().replace("-", "");
    }

    public static ResourceKey<LevelStem> vaultStemKey(UUID teamId) {
        return ResourceKey.create(Registries.LEVEL_STEM,
                OreVault.id("vault_" + teamIdNoHyphens(teamId)));
    }

    public static ResourceKey<Level> vaultDimensionKey(UUID teamId) {
        return ResourceKey.create(Registries.DIMENSION,
                OreVault.id("vault_" + teamIdNoHyphens(teamId)));
    }

    public static UUID teamIdFromDimensionKey(ResourceKey<Level> key) {
        if (key != null && key.location().getNamespace().equals(OreVault.MODID)
                && key.location().getPath().startsWith("vault_")) {
            String hex = key.location().getPath().substring("vault_".length());
            try {
                String dashed = hex.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5");
                return UUID.fromString(dashed);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    public static boolean isVaultDimension(Level level) {
        return teamIdFromDimensionKey(level.dimension()) != null;
    }

    /** Team of the player, resolved against the server. Falls back to null. */
    public static UUID teamIdFor(ServerPlayer player) {
        return teamForPlayer(player.getUUID()).map(Team::getTeamId).orElse(null);
    }

    public static MinecraftServer serverOf(Level level) {
        return level.getServer();
    }
}
