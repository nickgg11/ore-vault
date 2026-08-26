package com.orevault.orevault.network;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.reset.VaultReset;
import com.orevault.orevault.skill.NodeDef;
import com.orevault.orevault.skill.NodeDefs;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.team.TeamHelper;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * All Ore Vault networking. Team data is pushed from the server as a full snapshot so the
 * Tome UI is a pure render of server state; every mutation round-trips through the server.
 */
public final class ModNetwork {
    public static final String VERSION = "1.0.0";

    private ModNetwork() {
    }

    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(OreVault.MODID).versioned(VERSION);

        registrar.playToClient(ToastPayload.TYPE, ToastPayload.STREAM_CODEC, ToastPayload::handle);
        registrar.playToClient(TeamDataPayload.TYPE, TeamDataPayload.STREAM_CODEC, TeamDataPayload::handle);
        registrar.playToClient(ResetVoteSyncPayload.TYPE, ResetVoteSyncPayload.STREAM_CODEC, ResetVoteSyncPayload::handle);

        registrar.playToServer(SpendPointPayload.TYPE, SpendPointPayload.STREAM_CODEC, SpendPointPayload::handle);
        registrar.playToServer(RefundPayload.TYPE, RefundPayload.STREAM_CODEC, RefundPayload::handle);
        registrar.playToServer(ToggleTradeoffPayload.TYPE, ToggleTradeoffPayload.STREAM_CODEC, ToggleTradeoffPayload::handle);
        registrar.playToServer(TeamDataRequestPayload.TYPE, TeamDataRequestPayload.STREAM_CODEC, TeamDataRequestPayload::handle);
        registrar.playToServer(ResetRequestPayload.TYPE, ResetRequestPayload.STREAM_CODEC, ResetRequestPayload::handle);
        registrar.playToServer(ResetVotePayload.TYPE, ResetVotePayload.STREAM_CODEC, ResetVotePayload::handle);
    }

    // --- helpers ----------------------------------------------------------------

    public static void showToast(ServerPlayer player, Component title, Component message) {
        PacketDistributor.sendToPlayer(player, new ToastPayload(title, message));
    }

    /** Composes the full snapshot for the player's team and sends it; open=true opens the screen. */
    public static void sendTeamDataTo(ServerPlayer player, boolean open) {
        UUID teamId = TeamHelper.teamIdFor(player);
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(player.getServer(), teamId);
        PacketDistributor.sendToPlayer(player, TeamDataPayload.of(data, teamId, player, open));
    }

    // --- client-bound payloads ---------------------------------------------------

    public record ToastPayload(Component title, Component message) implements CustomPacketPayload {
        public static final Type<ToastPayload> TYPE = new Type<>(OreVault.id("toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToastPayload> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, ToastPayload::title,
                ComponentSerialization.STREAM_CODEC, ToastPayload::message,
                ToastPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ToastPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().getToastManager().addToast(
                            new net.minecraft.client.gui.components.toasts.SystemToast(
                                    net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.TUTORIAL_HINT,
                                    payload.title(), payload.message()));
                }
            });
        }
    }

    public record TeamDataPayload(CompoundTag data, boolean open) implements CustomPacketPayload {
        public static final Type<TeamDataPayload> TYPE = new Type<>(OreVault.id("team_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamDataPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, TeamDataPayload::data,
                ByteBufCodecs.BOOL, TeamDataPayload::open,
                TeamDataPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static TeamDataPayload of(OreVaultTeamData data, UUID teamId, ServerPlayer viewer, boolean open) {
            CompoundTag tag = new CompoundTag();
            tag.putString("teamId", teamId.toString());
            TeamHelper.teamById(teamId).ifPresent(team -> {
                tag.putString("teamName", team.getShortName());
                CompoundTag members = new CompoundTag();
                for (UUID memberId : team.getMembers()) {
                    CompoundTag memberTag = new CompoundTag();
                    memberTag.put("stats", data.statsFor(memberId).writeNbt());
                    memberTag.putBoolean("online", team.getOnlineMembers().stream()
                            .anyMatch(p -> p.getUUID().equals(memberId)));
                    members.put(memberId.toString(), memberTag);
                }
                tag.put("members", members);
            });

            CompoundTag res = new CompoundTag();
            SkillTree.TreeStateSnapshot rs = SkillTree.snapshot(data, NodeDefs.RESONANCE);
            res.putInt("level", rs.level());
            res.putInt("points", rs.points());
            res.putLong("pool", rs.pool());
            res.putLong("next", rs.nextThreshold());
            res.putLong("cost", rs.levelCost());
            tag.put("resonance", res);

            CompoundTag anim = new CompoundTag();
            SkillTree.TreeStateSnapshot as = SkillTree.snapshot(data, NodeDefs.ANIMUS);
            anim.putInt("level", as.level());
            anim.putInt("points", as.points());
            anim.putLong("pool", as.pool());
            anim.putLong("next", as.nextThreshold());
            anim.putLong("cost", as.levelCost());
            tag.put("animus", anim);

            CompoundTag nodes = new CompoundTag();
            data.unlockedNodes().forEach((id, tier) -> nodes.putInt(id, tier));
            tag.put("nodes", nodes);

            CompoundTag tradeoffs = new CompoundTag();
            data.tradeoffToggles().forEach((id, players) -> {
                tradeoffs.putBoolean(id, players.getOrDefault(viewer.getUUID(), false));
            });
            tag.put("tradeoffs", tradeoffs);

            CompoundTag playerStats = data.statsFor(viewer.getUUID()).writeNbt();
            tag.put("playerStats", playerStats);

            tag.putLong("chunksGenerated", data.chunksGenerated());
            tag.putLong("totalBlocksGenerated", data.totalBlocksGenerated());
            tag.putBoolean("vaultCreated", data.vaultCreated());
            tag.putBoolean("vaultExpanded", data.vaultExpanded());
            tag.putBoolean("resetEligible", VaultReset.viewerHoldsSovereignIgniter(viewer));
            tag.put("voteState", VaultReset.voteStateNbt(teamId));
            return new TeamDataPayload(tag, open);
        }

        public static void handle(TeamDataPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                var player = net.minecraft.client.Minecraft.getInstance().player;
                if (player != null) {
                    com.orevault.orevault.client.ClientPacketHandlers.handleTeamData(payload);
                }
            });
        }
    }

    public record ResetVoteSyncPayload(CompoundTag state) implements CustomPacketPayload {
        public static final Type<ResetVoteSyncPayload> TYPE = new Type<>(OreVault.id("reset_vote_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetVoteSyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, ResetVoteSyncPayload::state,
                ResetVoteSyncPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ResetVoteSyncPayload payload, IPayloadContext context) {
            context.enqueueWork(() ->
                    com.orevault.orevault.client.ClientPacketHandlers.handleVoteSync(payload.state()));
        }
    }

    // --- server-bound payloads ---------------------------------------------------

    public record SpendPointPayload(String nodeId) implements CustomPacketPayload {
        public static final Type<SpendPointPayload> TYPE = new Type<>(OreVault.id("spend_point"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SpendPointPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SpendPointPayload::nodeId,
                SpendPointPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(SpendPointPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    UUID teamId = TeamHelper.teamIdFor(player);
                    if (teamId == null) {
                        return;
                    }
                    OreVaultTeamData data = OreVaultTeamData.get(player.getServer(), teamId);
                    NodeDef def = NodeDefs.get(payload.nodeId()).orElse(null);
                    if (def == null || def.isHidden()) {
                        return;
                    }
                    SkillTree.UnlockResult result = SkillTree.canUnlock(data, def);
                    if (result.ok() && SkillTree.unlock(data, def)) {
                        onNodeChanged(player);
                        player.sendSystemMessage(Component.translatable("orevault.msg.unlocked",
                                Component.translatable(def.displayNameKey()), data.nodeTier(def.id())), false);
                    } else {
                        player.sendSystemMessage(result.message(), false);
                        sendTeamDataTo(player, false);
                    }
                }
            });
        }
    }

    public static void onNodeChanged(ServerPlayer player) {
        UUID teamId = TeamHelper.teamIdFor(player);
        if (teamId == null) {
            return;
        }
        OreVaultTeamData data = OreVaultTeamData.get(player.getServer(), teamId);
        // Vault Expansion applies after a reset; the reset button explains this.
        if (data.hasNode("vault_expansion")) {
            data.setVaultExpanded(false); // applied on next reset
        }
        sendTeamDataTo(player, false);
    }

    public record RefundPayload(String nodeId) implements CustomPacketPayload {
        public static final Type<RefundPayload> TYPE = new Type<>(OreVault.id("refund"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RefundPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RefundPayload::nodeId,
                RefundPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(RefundPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    UUID teamId = TeamHelper.teamIdFor(player);
                    if (teamId == null) {
                        return;
                    }
                    OreVaultTeamData data = OreVaultTeamData.get(player.getServer(), teamId);
                    NodeDef def = NodeDefs.get(payload.nodeId()).orElse(null);
                    if (def == null) {
                        return;
                    }
                    if (!SkillTree.canRefund(data, player, def.treeId(), def)) {
                        player.sendSystemMessage(Component.translatable("orevault.msg.refund_blocked"), false);
                        return;
                    }
                    if (SkillTree.refund(data, player, def)) {
                        player.sendSystemMessage(Component.translatable("orevault.msg.refunded",
                                Component.translatable(def.displayNameKey())), false);
                        onNodeChanged(player);
                    }
                }
            });
        }
    }

    public record ToggleTradeoffPayload(String nodeId, boolean active) implements CustomPacketPayload {
        public static final Type<ToggleTradeoffPayload> TYPE = new Type<>(OreVault.id("toggle_tradeoff"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleTradeoffPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ToggleTradeoffPayload::nodeId,
                ByteBufCodecs.BOOL, ToggleTradeoffPayload::active,
                ToggleTradeoffPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ToggleTradeoffPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    UUID teamId = TeamHelper.teamIdFor(player);
                    if (teamId == null) {
                        return;
                    }
                    OreVaultTeamData data = OreVaultTeamData.get(player.getServer(), teamId);
                    NodeDef def = NodeDefs.get(payload.nodeId()).orElse(null);
                    if (def == null || !def.tradeoff() || data.nodeTier(def.id()) < 1) {
                        return;
                    }
                    data.setTradeoff(def.id(), player.getUUID(), payload.active());
                    sendTeamDataTo(player, false);
                }
            });
        }
    }

    public record TeamDataRequestPayload(boolean open) implements CustomPacketPayload {
        public static final Type<TeamDataRequestPayload> TYPE = new Type<>(OreVault.id("team_data_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TeamDataRequestPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, TeamDataRequestPayload::open,
                TeamDataRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(TeamDataRequestPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    sendTeamDataTo(player, payload.open());
                }
            });
        }
    }

    public record ResetRequestPayload(boolean backup) implements CustomPacketPayload {
        public static final Type<ResetRequestPayload> TYPE = new Type<>(OreVault.id("reset_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetRequestPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ResetRequestPayload::backup,
                ResetRequestPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ResetRequestPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    VaultReset.startVote(player, payload.backup());
                }
            });
        }
    }

    public record ResetVotePayload(boolean approve) implements CustomPacketPayload {
        public static final Type<ResetVotePayload> TYPE = new Type<>(OreVault.id("reset_vote"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetVotePayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, ResetVotePayload::approve,
                ResetVotePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(ResetVotePayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player) {
                    VaultReset.castVote(player, payload.approve());
                }
            });
        }
    }
}

