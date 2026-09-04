package com.orevault.orevault.network;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.skill.TradeoffToggle;
import com.orevault.orevault.team.TeamHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's network channel and every packet on it (§8, §3.5, §4.4).
 *
 * <p>Registration happens on the <b>mod event bus</b> via
 * {@link RegisterPayloadHandlersEvent}, not {@code NeoForge.EVENT_BUS}. Putting
 * it on the game bus fails silently — the listener simply never fires and every
 * packet is an unknown-channel disconnect.</p>
 *
 * <h2>The server never trusts a payload</h2>
 *
 * <p>Every serverbound packet here carries only an intent: a node id, a vote.
 * It never carries a cost, a skill-point balance, or a result. The server
 * re-derives all of that from its own {@link OreVaultTeamData} and decides. A
 * client saying "I bought the keystone" is a client making a request that will
 * be checked, which is the only shape that survives a modified client — and the
 * skill tree is the one thing in this mod worth cheating at.</p>
 *
 * <p>Handlers run through {@link IPayloadContext#enqueueWork} because
 * {@code SavedData} is main-thread-only; a payload handler is not on the main
 * thread by default.</p>
 *
 * <h2>What is wired and what is not</h2>
 *
 * <p>The two serverbound gameplay packets — purchase and tradeoff toggle — do
 * real work now, because {@code SkillTree} and {@code TradeoffToggle} already
 * exist and a no-op purchase handler is a security hole waiting for someone to
 * fill it in a hurry. The clientbound packets are defined with their real
 * payload shape and are routed to {@code ClientPacketHandlers} through the
 * callback in {@link #setClientHandler}, which stores them for the Tome screens
 * ([34]–[35]) to read. Reset voting (§3.5)
 * is defined and rejected with a log line until Phase 8 (#94) builds the state
 * machine behind it.</p>
 */
public final class ModNetwork {

    /**
     * Bumping this string breaks connections to servers on the old version.
     * It changes when a payload's fields change, not when a handler does.
     */
    public static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    // ----- payloads -----

    /** Client asks to buy the next tier of a node (§4.4). Cost is decided server-side. */
    public record PurchaseNode(String nodeId) implements CustomPacketPayload {
        public static final Type<PurchaseNode> TYPE = new Type<>(id("purchase_node"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseNode> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, PurchaseNode::nodeId, PurchaseNode::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client asks to flip a purchased tradeoff node (§6.1).
     *
     * <p>Carries no desired state. The server flips whatever it currently holds,
     * so a replayed or reordered packet cannot force a node into the state the
     * sender wanted — it can only toggle.</p>
     */
    public record ToggleTradeoff(String nodeId) implements CustomPacketPayload {
        public static final Type<ToggleTradeoff> TYPE = new Type<>(id("toggle_tradeoff"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleTradeoff> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ToggleTradeoff::nodeId, ToggleTradeoff::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client casts a Vault reset vote (§3.5). Rejected until Phase 8 (#94). */
    public record CastResetVote(boolean approve) implements CustomPacketPayload {
        public static final Type<CastResetVote> TYPE = new Type<>(id("cast_reset_vote"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CastResetVote> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, CastResetVote::approve, CastResetVote::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server pushes the team's Resonance state for the Tome's header (§8).
     *
     * <p>The pool is sent as a long and the progress as a float rather than
     * sending the curve and letting the client derive them. The curve depends on
     * {@code curve_divisor}, which is server config the client has no copy of,
     * so a client-side derivation would quietly disagree with the server on any
     * pack that tunes it.</p>
     */
    public record SyncTeamProgress(long pool, int level, int unspentPoints, float progressToNext)
            implements CustomPacketPayload {
        public static final Type<SyncTeamProgress> TYPE = new Type<>(id("sync_team_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncTeamProgress> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, SyncTeamProgress::pool,
                        ByteBufCodecs.VAR_INT, SyncTeamProgress::level,
                        ByteBufCodecs.VAR_INT, SyncTeamProgress::unspentPoints,
                        ByteBufCodecs.FLOAT, SyncTeamProgress::progressToNext,
                        SyncTeamProgress::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server broadcasts the state of a running reset vote (§3.5). Phase 8 (#94). */
    public record ResetVoteStatus(int votesFor, int votesNeeded, int secondsRemaining)
            implements CustomPacketPayload {
        public static final Type<ResetVoteStatus> TYPE = new Type<>(id("reset_vote_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ResetVoteStatus> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, ResetVoteStatus::votesFor,
                        ByteBufCodecs.VAR_INT, ResetVoteStatus::votesNeeded,
                        ByteBufCodecs.VAR_INT, ResetVoteStatus::secondsRemaining,
                        ResetVoteStatus::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(OreVault.MODID, path);
    }

    // ----- registration -----

    /** Registers every payload. Subscribed from {@code OreVault} on the mod event bus. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.playToServer(PurchaseNode.TYPE, PurchaseNode.CODEC, ModNetwork::onPurchaseNode);
        registrar.playToServer(ToggleTradeoff.TYPE, ToggleTradeoff.CODEC, ModNetwork::onToggleTradeoff);
        registrar.playToServer(CastResetVote.TYPE, CastResetVote.CODEC, ModNetwork::onCastResetVote);

        // Clientbound: routed to the client callback, which is installed only
        // on a client. On a dedicated server these register so the channel
        // matches, and never fire.
        registrar.playToClient(SyncTeamProgress.TYPE, SyncTeamProgress.CODEC, ModNetwork::onClientPayload);
        registrar.playToClient(ResetVoteStatus.TYPE, ResetVoteStatus.CODEC, ModNetwork::onClientPayload);
    }

    // ----- serverbound handlers -----

    private static void onPurchaseNode(PurchaseNode payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            UUID teamId = TeamHelper.getTeamId(player);
            ServerLevel overworld = player.level().getServer().overworld();
            OreVaultTeamData data = OreVaultTeamData.getOrCreate(overworld, teamId);

            // Level and points come from the server's own record, never the packet.
            SkillTree tree = data.resonanceTree();
            int teamLevel = ResonanceSystem.levelOf(data, ResonanceSystem.curve());
            int available = data.getResonanceSkillPoints();

            int cost = tree.nextTierCost(payload.nodeId());
            SkillTree.UnlockResult result = tree.unlock(payload.nodeId(), teamLevel, available);
            if (result != SkillTree.UnlockResult.OK) {
                OreVault.LOGGER.debug("Purchase of {} by {} refused: {}",
                        payload.nodeId(), player.getGameProfile().name(), result);
                return;
            }

            data.addResonanceSkillPoints(-cost);
            data.setDirty();
            OreVault.LOGGER.debug("{} purchased {} for {} point(s)",
                    player.getGameProfile().name(), payload.nodeId(), cost);
        });
    }

    private static void onToggleTradeoff(ToggleTradeoff payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            UUID teamId = TeamHelper.getTeamId(player);
            ServerLevel overworld = player.level().getServer().overworld();
            OreVaultTeamData data = OreVaultTeamData.getOrCreate(overworld, teamId);
            PlayerStats stats = data.getOrCreatePlayerStats(player.getUUID());

            // The overload that takes the player is the one that knows about the
            // in-Vault restriction (#104); the packet cannot bypass it.
            TradeoffToggle.Result result =
                    TradeoffToggle.toggle(player, data.resonanceTree(), stats, payload.nodeId());
            if (result.allowed()) {
                data.setDirty();
            }
        });
    }

    private static void onCastResetVote(CastResetVote payload, IPayloadContext context) {
        // §3.5 needs a vote state machine, a countdown and an evacuation pass,
        // none of which exist. Accepting votes into nothing would look like the
        // feature half-works; refusing loudly is the honest state until #94.
        OreVault.LOGGER.warn("Reset vote received (approve={}) but the reset flow is not implemented (#94)",
                payload.approve());
    }

    // ----- clientbound -----

    /**
     * The client's handler for clientbound payloads, installed by
     * {@code OreVaultClient} at startup ([39]).
     *
     * <p>A callback rather than a direct call. This class is common code and
     * cannot name anything under {@code client/} — a common-path reference to a
     * client class kills a dedicated server at class-load, and no unit test in
     * this tree catches it. Holding a functional interface keeps the reference
     * one-way: the client knows about the network, the network does not know
     * about the client.</p>
     */
    @FunctionalInterface
    public interface ClientHandler {
        void handle(CustomPacketPayload payload, IPayloadContext context);
    }

    private static volatile @Nullable ClientHandler clientHandler;

    /** Installs the client's payload handler. Called only from client code. */
    public static void setClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    /**
     * Routes a clientbound payload to the client handler.
     *
     * <p>Stays silent-but-logged when none is installed. That is the normal
     * state on a dedicated server, which registers these payload types so the
     * channel matches the client's but will never receive one.</p>
     */
    private static void onClientPayload(CustomPacketPayload payload, IPayloadContext context) {
        ClientHandler handler = clientHandler;
        if (handler == null) {
            OreVault.LOGGER.debug("Received {} with no client handler installed", payload.type().id());
            return;
        }
        handler.handle(payload, context);
    }
}
