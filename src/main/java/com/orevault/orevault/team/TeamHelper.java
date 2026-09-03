package com.orevault.orevault.team;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.orevault.orevault.OreVault;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Thin wrapper around the FTB Teams API so the rest of the mod never touches
 * FTB Teams types directly.
 *
 * <p>All methods are server-side. Every player always has a team (§2: solo
 * players are a team of one), so {@code getTeamId} falls back to the player's
 * own UUID if the manager has no record yet.</p>
 */
public final class TeamHelper {

    private TeamHelper() {
    }

    public static TeamManager manager() {
        return FTBTeamsAPI.api().getManager();
    }

    public static Optional<Team> getTeam(ServerPlayer player) {
        return manager().getTeamForPlayerID(player.getUUID());
    }

    public static Optional<Team> getTeam(UUID playerId) {
        return manager().getTeamForPlayerID(playerId);
    }

    public static Optional<Team> getTeamById(UUID teamId) {
        return manager().getTeamByID(teamId);
    }

    /** The player's effective FTB team id; falls back to the player UUID (solo). */
    public static UUID getTeamId(ServerPlayer player) {
        return getTeam(player).map(Team::getTeamId).orElse(player.getUUID());
    }

    /** The player's effective FTB team id; falls back to the player UUID (solo). */
    public static UUID getTeamId(UUID playerId) {
        return getTeam(playerId).map(Team::getTeamId).orElse(playerId);
    }

    /** Currently online members of the given team (server-side only). */
    public static Collection<ServerPlayer> getOnlineTeamMembers(UUID teamId) {
        return getTeamById(teamId).map(Team::getOnlineMembers).orElse(List.of());
    }

    /**
     * Total member count of the given team.
     *
     * <p><b>Falls back to 1, not 0.</b> Every player is a team of at least
     * themselves (§2), and {@link #getTeamId} already returns the player's own
     * UUID when FTB Teams has no record — a UUID that is deliberately not a team
     * id, so looking it up here finds nothing. Returning 0 for that case fed
     * {@code TeamScaling.teamPoolGain} a divisor of zero, which throws, on the
     * orb-pickup path. A team that genuinely has no members cannot be earning
     * Resonance, so 1 is the only answer that is ever right.</p>
     */
    public static int teamSize(UUID teamId) {
        return getTeamById(teamId)
                .map(team -> Math.max(1, team.getMembers().size()))
                .orElse(1);
    }

    /** Whether the player's team has at most one member (solo). */
    public static boolean isSoloPlayer(ServerPlayer player) {
        return getTeam(player).map(team -> team.getMembers().size() <= 1).orElse(true);
    }

    /** Dimension registry key for a team: {@code orevault:vault_<uuid-without-hyphens>} (§3.1). */
    public static Identifier dimensionKey(UUID teamId) {
        return Identifier.fromNamespaceAndPath(OreVault.MODID, "vault_" + teamId.toString().replace("-", ""));
    }
}
