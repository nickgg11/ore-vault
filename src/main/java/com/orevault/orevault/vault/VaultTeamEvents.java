package com.orevault.orevault.vault;

import com.orevault.orevault.data.DataPaths;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.team.TeamHelper.TeamChange;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one subscriber to each of {@link TeamHelper}'s hooks (ADR 0002 "Team change while inside", Hooks), so {@code team}
 * imports neither {@code vault} nor {@code data}. The hooks' lists are static and never cleared, so this is registered once
 * from the mod constructor and not at a server event, or it would double on a second world in one client.
 */
public final class VaultTeamEvents {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultTeamEvents.class);

    private VaultTeamEvents() {
    }

    /** Called once from the mod constructor, after {@link TeamHelper#register()}. */
    public static void register() {
        TeamHelper.onTeamChange(VaultTeamEvents::onTeamChange);
        TeamHelper.onDisband(VaultTeamEvents::onDisband);
    }

    /**
     * ADR 0002 fixes the order of what grows here: rebuild both teams' views (E9-S3), mark {@code data.SyncOutbox}, then send
     * a player out of a Vault that is no longer theirs (E3-S6a). Nothing is copied between team files, so a change writes no
     * team data (S18, §9). The change FTB Teams posts when it first makes a personal team has a null previous team and is
     * not a change of team at all (§11).
     */
    private static void onTeamChange(TeamChange change) {
        if (change.previousTeamId() == null) {
            return;
        }
        LOGGER.debug("Player {} moved from team {} to {}", change.playerId(), change.previousTeamId(), change.teamId());
    }

    /**
     * ADR 0007's Disband step 1. A team that never wrote data and has no Vault folder has nothing to archive, so nothing is
     * written. Evacuating anyone inside, step 2, and the archive move are E11-S6's. The event carries no server. The write
     * can be lost, if a damaged team file cannot be copied aside ({@link OreVaultTeamData#forWrite}), so it is read back.
     */
    private static void onDisband(UUID teamId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (!OreVaultTeamData.hasRecord(server, teamId) && !Files.exists(DataPaths.vaultFolder(server, teamId))) {
            return;
        }
        OreVaultTeamData.forWrite(server, teamId).markDisbanded(server.overworld().getGameTime());
        server.getDataStorage().saveAndJoin();
        if (OreVaultTeamData.read(server, teamId).disbandedAt().isEmpty()) {
            LOGGER.error("Team {} disbanded, but disbanded_at could not be recorded: its team file is damaged and could not be "
                    + "copied aside. Its Vault stays as if the team still existed", teamId);
            return;
        }
        LOGGER.info("Team {} disbanded; disbanded_at saved", teamId);
    }
}
