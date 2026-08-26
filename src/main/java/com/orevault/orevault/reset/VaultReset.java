package com.orevault.orevault.reset;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dimension reset with team voting (design spec section 3.5): majority rule, 10-second
 * countdown, optional region backup, then delete + fresh re-registration of the dimension.
 * All progression (levels, pools, points, stats) is preserved.
 */
public final class VaultReset {
    public record VoteState(UUID teamId, boolean backup, Map<UUID, Boolean> votes, int required,
                            int countdown, boolean active, boolean passed) {
        CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putString("teamId", teamId.toString());
            tag.putBoolean("backup", backup);
            CompoundTag v = new CompoundTag();
            votes.forEach((u, b) -> v.putBoolean(u.toString(), b));
            tag.put("votes", v);
            tag.putInt("required", required);
            tag.putInt("countdown", countdown);
            tag.putBoolean("active", active);
            tag.putBoolean("passed", passed);
            return tag;
        }
    }

    private static final Map<UUID, VoteState> ACTIVE_VOTES = new HashMap<>();
    private static final int VOTE_TIMEOUT_TICKS = 1200; // 60s to gather votes
    private static int voteAge;

    private VaultReset() {
    }

    public static boolean viewerHoldsSovereignIgniter(ServerPlayer player) {
        for (var stack : player.getInventory().items) {
            if (stack.is(ModItems.SOVEREIGN_VAULT_IGNITER.get())) {
                return true;
            }
        }
        return false;
    }

    public static CompoundTag voteStateNbt(UUID teamId) {
        VoteState state = ACTIVE_VOTES.get(teamId);
        return state == null ? new CompoundTag() : state.toNbt();
    }

    public static void startVote(ServerPlayer initiator, boolean backup) {
        if (!viewerHoldsSovereignIgniter(initiator)) {
            initiator.sendSystemMessage(Component.translatable("orevault.msg.reset_need_igniter"), false);
            return;
        }
        UUID teamId = TeamHelper.teamIdFor(initiator);
        if (teamId == null) {
            return;
        }
        if (ACTIVE_VOTES.containsKey(teamId)) {
            initiator.sendSystemMessage(Component.translatable("orevault.msg.reset_vote_active"), false);
            return;
        }
        Team team = TeamHelper.teamById(teamId).orElse(null);
        if (team == null) {
            return;
        }
        var online = team.getOnlineMembers();
        int onlineCount = online.size();
        if (onlineCount == 0) {
            return;
        }
        if (!OreVaultServerConfig.ALLOW_BACKUP_ON_RESET.get() && backup) {
            initiator.sendSystemMessage(Component.translatable("orevault.msg.reset_backup_disabled"), false);
            return;
        }
        int required = switch (onlineCount) {
            case 1 -> 1;
            case 2 -> 2;
            default -> onlineCount / 2 + 1;
        };
        VoteState state = new VoteState(teamId, backup, new HashMap<>(), required, 0, true, false);
        ACTIVE_VOTES.put(teamId, state);
        voteAge = 0;
        for (ServerPlayer member : online) {
            ModNetwork.sendTeamDataTo(member, false);
            PacketDistributor.sendToPlayer(member, new ModNetwork.ResetVoteSyncPayload(state.toNbt()));
        }
        OreVault.LOGGER.info("Ore Vault: reset vote started for team {} ({} online, {} needed, backup={})",
                teamId, onlineCount, required, backup);
    }

    public static void castVote(ServerPlayer voter, boolean approve) {
        UUID teamId = TeamHelper.teamIdFor(voter);
        if (teamId == null) {
            return;
        }
        VoteState state = ACTIVE_VOTES.get(teamId);
        if (state == null || !state.active()) {
            return;
        }
        Map<UUID, Boolean> votes = new HashMap<>(state.votes());
        votes.put(voter.getUUID(), approve);
        long yes = votes.values().stream().filter(Boolean::booleanValue).count();
        VoteState updated = new VoteState(state.teamId(), state.backup(), votes, state.required(),
                state.countdown(), true, yes >= state.required());
        if (!updated.passed()) {
            ACTIVE_VOTES.put(teamId, updated);
            broadcast(teamId, updated);
            return;
        }
        // Vote passed: 10-second countdown begins.
        updated = new VoteState(state.teamId(), state.backup(), votes, state.required(), 200, true, true);
        ACTIVE_VOTES.put(teamId, updated);
        broadcast(teamId, updated);
        TeamHelper.teamById(teamId).ifPresent(team -> {
            Component warning = Component.translatable("orevault.msg.reset_countdown");
            for (ServerPlayer member : team.getOnlineMembers()) {
                if (member.level().dimension().equals(TeamHelper.vaultDimensionKey(teamId))) {
                    member.sendSystemMessage(warning, false);
                }
            }
        });
    }

    private static void broadcast(UUID teamId, VoteState state) {
        TeamHelper.teamById(teamId).ifPresent(team -> {
            for (ServerPlayer member : team.getOnlineMembers()) {
                PacketDistributor.sendToPlayer(member, new ModNetwork.ResetVoteSyncPayload(state.toNbt()));
            }
        });
    }

    /** Server tick: countdown and timeout handling. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE_VOTES.isEmpty()) {
            return;
        }
        if (++voteAge > VOTE_TIMEOUT_TICKS) {
            // Timeout: expire votes that haven't passed.
            for (var it = ACTIVE_VOTES.entrySet().iterator(); it.hasNext(); ) {
                VoteState state = it.next().getValue();
                if (!state.passed()) {
                    it.remove();
                    broadcast(state.teamId(), new VoteState(state.teamId(), false, Map.of(), 0, 0, false, false));
                }
            }
            return;
        }
        for (var it = ACTIVE_VOTES.entrySet().iterator(); it.hasNext(); ) {
            VoteState state = it.next().getValue();
            if (!state.passed()) {
                continue;
            }
            int countdown = state.countdown() - 1;
            if (countdown <= 0) {
                it.remove();
                executeReset(server, state);
                broadcast(state.teamId(), new VoteState(state.teamId(), false, Map.of(), 0, 0, false, false));
            } else {
                ACTIVE_VOTES.put(state.teamId(), new VoteState(state.teamId(), state.backup(), state.votes(),
                        state.required(), countdown, true, true));
                if (countdown % 20 == 0) {
                    broadcast(state.teamId(), ACTIVE_VOTES.get(state.teamId()));
                }
            }
        }
    }

    private static void executeReset(MinecraftServer server, VoteState state) {
        UUID teamId = state.teamId();
        TeamHelper.teamById(teamId).ifPresent(team -> {
            ServerLevel overworld = server.overworld();
            for (ServerPlayer member : team.getOnlineMembers()) {
                if (member.level().dimension().equals(TeamHelper.vaultDimensionKey(teamId))) {
                    member.teleport(new net.minecraft.world.level.portal.TeleportTransition(
                            overworld, net.minecraft.world.phys.Vec3.atCenterOf(overworld.getSharedSpawnPos()),
                            net.minecraft.world.phys.Vec3.ZERO, member.getYRot(), member.getXRot(),
                            net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                }
            }
        });
        boolean ok = VaultDimensions.resetVault(server, teamId, state.backup());
        OreVaultTeamData data = OreVaultTeamData.get(server, teamId);
        data.anchorPositions().clear();
        data.zonePositions().clear();
        OreVault.LOGGER.info("Ore Vault: team {} reset their Vault (backup={}, success={}) at {}",
                teamId, state.backup(), ok, java.time.Instant.now());
    }
}

