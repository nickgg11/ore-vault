package com.orevault.orevault.chunk;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.UUID;

/**
 * Vault Anchor chunk loading (design spec section 3.4) via NeoForge 26.1 TicketController.
 * Ticket limits come from the Vault Presence node, capped by the admin config ceiling.
 */
public final class VaultChunkLoader {
    public static final TicketController CONTROLLER = new TicketController(
            OreVault.id("vault_anchor"),
            VaultChunkLoader::validateTickets
    );

    private VaultChunkLoader() {
    }

    public static void registerControllers(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Max simultaneous loaded chunks per team, from nodes + config. */
    public static int maxTicketsForTeam(OreVaultTeamData data) {
        if (!OreVaultServerConfig.VAULT_PRESENCE_ENABLED.get()) {
            return 0;
        }
        int fromNodes = switch (data.nodeTier("vault_presence")) {
            case 1 -> 4;
            case 2 -> 12;
            case 3 -> 28;
            default -> 0;
        };
        int ceiling = OreVaultServerConfig.MAX_LOADED_CHUNKS_PER_TEAM.get();
        if (ceiling <= 0) {
            return fromNodes;
        }
        return Math.min(fromNodes, ceiling);
    }

    public static void onAnchorPlaced(ServerLevel level, BlockPos pos) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        int max = maxTicketsForTeam(data);
        if (max <= 0 || data.anchorPositions().size() >= max) {
            return;
        }
        data.addAnchor(pos);
        ChunkPos chunk = ChunkPos.containing(pos);
        CONTROLLER.forceChunk(level, teamId, chunk.x(), chunk.z(), true, true);
        OreVault.LOGGER.debug("Ore Vault: anchor placed at {} for team {} (tickets: {})",
                pos, teamId, data.anchorPositions().size());
    }

    public static void onAnchorRemoved(ServerLevel level, BlockPos pos) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        data.removeAnchor(pos);
        ChunkPos chunk = ChunkPos.containing(pos);
        CONTROLLER.forceChunk(level, teamId, chunk.x(), chunk.z(), false, false);
    }

    /** Called when stored tickets are re-activated after a restart: drop stale ones. */
    private static void validateTickets(ServerLevel level, net.neoforged.neoforge.common.world.chunk.TicketHelper ticketHelper) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            ticketHelper.getBlockTickets().keySet().forEach(ticketHelper::removeAllTickets);
            ticketHelper.getEntityTickets().keySet().forEach(ticketHelper::removeAllTickets);
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        ticketHelper.getEntityTickets().forEach((uuid, tickets) -> {
            if (!teamId.equals(uuid)) {
                ticketHelper.removeAllTickets(uuid);
            }
        });
        ticketHelper.getBlockTickets().forEach((pos, tickets) -> {
            if (!data.anchorPositions().contains(pos) || !level.getBlockState(pos).is(ModBlocks.VAULT_ANCHOR)) {
                ticketHelper.removeAllTickets(pos);
            }
        });
    }

    /**
     * Periodic sweep (26.1 has no Block#onRemove): deregisters anchors whose block is gone.
     */
    public static void sweep(ServerLevel level) {
        UUID teamId = TeamHelper.teamIdFromDimensionKey(level.dimension());
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(level.getServer(), teamId);
        for (BlockPos pos : new java.util.ArrayList<>(data.anchorPositions())) {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(ModBlocks.VAULT_ANCHOR)) {
                onAnchorRemoved(level, pos);
            }
        }
    }
}

