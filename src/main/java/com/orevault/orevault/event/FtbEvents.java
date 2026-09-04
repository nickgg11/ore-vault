package com.orevault.orevault.event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamDeletedEvent;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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

    /** Persistent-data flag: this player has already been given a Tome ([33]). */
    private static final String TOME_GRANTED_TAG = "orevault_tome_granted";

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

    /**
     * Grants the Tome on first join (§8, [33]).
     *
     * <p><b>Listens to the vanilla login event, not FTB's party-join event.</b>
     * {@code PlayerJoinedPartyTeamEvent} fires only when someone joins a
     * <em>party</em>, and a solo player never joins one — so hooking it, as the
     * ticket originally described, would hand a Tome to everyone except the
     * players most likely to be playing alone. Every player is a team of one
     * (§2), so the grant has to key off the player existing, not off a party.</p>
     *
     * <p>Granted once, tracked by a flag in persistent data rather than by
     * scanning the inventory. Scanning would re-grant the book every login to
     * anyone who deliberately binned it, which reads as the mod fighting the
     * player. Losing it is recoverable through the recipe ([67]).</p>
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBooleanOr(TOME_GRANTED_TAG, false)) {
            return;
        }
        data.putBoolean(TOME_GRANTED_TAG, true);

        ItemStack tome = new ItemStack(ModItems.TOME.get());
        if (!player.getInventory().add(tome)) {
            player.drop(tome, false);
        }
        OreVault.LOGGER.info("Granted the Tome of the Deep Seam to {}", player.getGameProfile().name());
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
