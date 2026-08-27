package com.orevault.orevault.event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamDeletedEvent;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * FTB Teams lifecycle hooks (§9, §11):
 * <ul>
 *   <li>team created → initialize the team's {@link OreVaultTeamData}</li>
 *   <li>team deleted → remove the team's saved data and log the deletion
 *       (Vault dimension deletion is deferred to the dimension/reset work)</li>
 * </ul>
 *
 * <p>Registered on the game event bus by {@link OreVault}.</p>
 */
public final class FtbEvents {

    private FtbEvents() {
    }

    @SubscribeEvent
    public static void onTeamCreated(FTBTeamsEvent.TeamCreated event) {
        TeamCreatedEvent.Data data = event.getEventData();
        UUID teamId = data.team().getTeamId();
        MinecraftServer server = TeamHelper.manager().getServer();
        ServerLevel overworld = server.overworld();
        OreVaultTeamData.getOrCreate(overworld, teamId);
        OreVault.LOGGER.info("Initialized Ore Vault team data for team {}", teamId);
    }

    @SubscribeEvent
    public static void onTeamDeleted(FTBTeamsEvent.TeamDeleted event) {
        TeamDeletedEvent.Data data = event.getEventData();
        UUID teamId = data.team().getTeamId();
        MinecraftServer server = TeamHelper.manager().getServer();
        ServerLevel overworld = server.overworld();

        // TODO [31]: delete the team's Vault dimension (VaultReset owns dimension deletion).
        OreVault.LOGGER.warn("Team {} disbanded: Vault dimension deletion not implemented yet (see [13]/[31])", teamId);

        OreVaultTeamData teamData = OreVaultTeamData.get(overworld, teamId);
        if (teamData != null) {
            teamData.setDirty(false); // never re-save data for a deleted team
        }
        deleteDataFile(server, teamId);
        OreVault.LOGGER.info("Deleted Ore Vault team data for team {}", teamId);
    }

    /** Deletes the team's SavedData file ({@code <world>/data/orevault/team_<uuid>.dat}). */
    private static void deleteDataFile(MinecraftServer server, UUID teamId) {
        Path file = server.getWorldPath(LevelResource.DATA)
                .resolve("orevault")
                .resolve("team_" + teamId + ".dat");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            OreVault.LOGGER.error("Failed to delete team data file {}", file, e);
        }
    }
}
