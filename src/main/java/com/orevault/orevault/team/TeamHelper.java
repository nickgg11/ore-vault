package com.orevault.orevault.team;

import com.orevault.orevault.core.team.TeamSize;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * The only class that imports FTB Teams (rule 5, §9), so that nothing else holds an FTB type: the rest of the mod
 * deals in team ids. Server side only, and only once FTB Teams has its manager, which it makes at
 * {@code ServerAboutToStartEvent}.
 *
 * <p>Every player has a team (§2, §3.2), so nothing here asks whether one does. A player FTB Teams does not know yet
 * answers with their own UUID, which is the id FTB Teams gives their personal team when it makes it.
 *
 * <p>Team ids are read with {@link Team#getId()} and not {@link Team#getTeamId()}. A personal team's {@code getTeamId}
 * answers its effective team, so by the time a player's team change is posted, the personal team they left names the
 * party they joined, and the change would read as a move from the party to itself.
 */
public final class TeamHelper {
    private static final List<Consumer<TeamChange>> TEAM_CHANGES = new CopyOnWriteArrayList<>();
    private static final List<Consumer<UUID>> DISBANDS = new CopyOnWriteArrayList<>();

    /**
     * A player's team changed, online or offline (§11).
     *
     * @param previousTeamId null only for the change FTB Teams posts when it first makes a player's personal team
     */
    public record TeamChange(UUID playerId, @Nullable UUID previousTeamId, UUID teamId) {
    }

    private TeamHelper() {
    }

    /** Called once from the mod constructor. These are gameplay events, so they go on the game bus (rule 3). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(FTBTeamsEvent.PlayerChangedTeam.class, TeamHelper::onPlayerChangedTeam);
        NeoForge.EVENT_BUS.addListener(FTBTeamsEvent.TeamDeleted.class, TeamHelper::onTeamDeleted);
    }

    /** Runs {@code listener} on each team change, on the main thread. Call once at mod init: listeners are never removed. */
    public static void onTeamChange(Consumer<TeamChange> listener) {
        TEAM_CHANGES.add(listener);
    }

    /**
     * Runs {@code listener} with the party's id when its last member leaves, the only disband there is (§9). Call once at
     * mod init: listeners are never removed.
     */
    public static void onDisband(Consumer<UUID> listener) {
        DISBANDS.add(listener);
    }

    public static UUID teamId(ServerPlayer player) {
        return teamId(player.getUUID());
    }

    public static UUID teamId(UUID playerId) {
        return manager().getTeamForPlayerID(playerId).map(Team::getId).orElse(playerId);
    }

    /** Every member, offline ones too. Empty for an id that is no team, and for a personal team while its player is in a party. */
    public static Set<UUID> members(UUID teamId) {
        return team(teamId).map(Team::getMembers).orElse(Set.of());
    }

    public static Collection<ServerPlayer> onlineMembers(UUID teamId) {
        return team(teamId).map(Team::getOnlineMembers).orElse(List.of());
    }

    /** Never below 1, for an unknown team as for an empty one ({@link TeamSize}). */
    public static int size(UUID teamId) {
        return TeamSize.of(members(teamId).size());
    }

    private static Optional<Team> team(UUID teamId) {
        return manager().getTeamByID(teamId);
    }

    private static TeamManager manager() {
        return FTBTeamsAPI.api().getManager();
    }

    private static void onPlayerChangedTeam(FTBTeamsEvent.PlayerChangedTeam event) {
        PlayerChangedTeamEvent.Data data = event.getEventData();
        Team previous = data.previousTeam();
        TeamChange change = new TeamChange(data.playerId(), previous == null ? null : previous.getId(), data.team().getId());
        TEAM_CHANGES.forEach(listener -> listener.accept(change));
    }

    /** A server team's delete posts this event too, and it is no disband: only parties own what §9 archives. */
    private static void onTeamDeleted(FTBTeamsEvent.TeamDeleted event) {
        Team team = event.getEventData().team();
        if (team.isPartyTeam()) {
            UUID teamId = team.getId();
            DISBANDS.forEach(listener -> listener.accept(teamId));
        }
    }
}
