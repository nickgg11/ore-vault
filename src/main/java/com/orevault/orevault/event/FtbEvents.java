package com.orevault.orevault.event;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;
import dev.ftb.mods.ftbteams.api.event.PlayerChangedTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamCreatedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamDeletedEvent;
import dev.ftb.mods.ftbteams.api.event.TeamPlayerLoggedInEvent;
import dev.ftb.mods.ftbteams.api.neoforge.FTBTeamsEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * FTB Teams event hooks (design spec section 11): team creation initialises SavedData,
 * login hands out the Tome of the Deep Seam, disband deletes the team's Vault dimension.
 */
public final class FtbEvents {
    private FtbEvents() {
    }

    @SubscribeEvent
    public static void onTeamCreated(FTBTeamsEvent.TeamCreated event) {
        var data = event.getEventData();
        MinecraftServer server = data.creator().getServer();
        OreVaultTeamData.get(server, data.team().getTeamId());
        OreVault.LOGGER.info("Ore Vault: initialised data for new team {}", data.team().getTeamId());
    }

    @SubscribeEvent
    public static void onTeamDeleted(FTBTeamsEvent.TeamDeleted event) {
        var data = event.getEventData();
        UUID teamId = data.team().getTeamId();
        MinecraftServer server = data.team().getOnlineMembers().stream()
                .findFirst().map(p -> p.getServer()).orElseGet(() -> null);
        if (server == null) {
            server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        }
        if (server == null) {
            return;
        }
        OreVault.LOGGER.info("Ore Vault: team {} disbanded — deleting Vault dimension", teamId);
        VaultDimensions.deleteVault(server, teamId, false);
        OreVaultTeamData teamData = OreVaultTeamData.get(server, teamId);
        teamData.setVaultCreated(false);
        teamData.anchorPositions().clear();
        teamData.zonePositions().clear();
    }

    @SubscribeEvent
    public static void onTeamPlayerLoggedIn(FTBTeamsEvent.TeamPlayerLoggedIn event) {
        var data = event.getEventData();
        ServerPlayer player = data.player();
        OreVaultTeamData teamData = OreVaultTeamData.get(player.getServer(), data.team().getTeamId());
        teamData.statsFor(player.getUUID()); // ensure stats exist
        giveTomeIfMissing(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedTeam(FTBTeamsEvent.PlayerChangedTeam event) {
        var data = event.getEventData();
        // Re-key nothing is needed — data is per-team and stats per-player; stats simply
        // follow the player to the new team's view.
        if (data.player() != null) {
            giveTomeIfMissing(data.player());
        }
    }

    private static void giveTomeIfMissing(ServerPlayer player) {
        boolean hasTome = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(ModItems.TOME_OF_THE_DEEP_SEAM.get()));
        if (!hasTome) {
            player.getInventory().add(new ItemStack(ModItems.TOME_OF_THE_DEEP_SEAM.get()));
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("orevault.msg.tome_given"), false);
        }
    }
}

