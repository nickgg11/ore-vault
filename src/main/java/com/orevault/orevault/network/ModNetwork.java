package com.orevault.orevault.network;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.skill.LevelCurve;
import com.orevault.orevault.skill.SkillTree;
import com.orevault.orevault.skill.TradeoffToggle;
import com.orevault.orevault.team.TeamHelper;

import io.netty.buffer.ByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
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
     *
     * <p>2: {@link SyncTeamProgress} carries both trees instead of Resonance
     * alone ([34]). 3: {@link SyncSkillTree} added ([35]).</p>
     */
    public static final String PROTOCOL_VERSION = "3";

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
     * One tree's state, as the Tome's header draws it (§8).
     *
     * <p>The pool is sent as a long and the progress as a float rather than
     * sending the curve and letting the client derive them. The curve depends on
     * {@code curve_divisor}, which is server config the client has no copy of,
     * so a client-side derivation would quietly disagree with the server on any
     * pack that tunes it.</p>
     */
    public record TreeProgress(long pool, int level, int unspentPoints, float progressToNext) {
        public static final StreamCodec<ByteBuf, TreeProgress> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, TreeProgress::pool,
                        ByteBufCodecs.VAR_INT, TreeProgress::level,
                        ByteBufCodecs.VAR_INT, TreeProgress::unspentPoints,
                        ByteBufCodecs.FLOAT, TreeProgress::progressToNext,
                        TreeProgress::new);
    }

    /**
     * Server pushes both skill trees' state for the Tome's header (§8).
     *
     * <p>Both travel in one packet because the header draws them together and
     * they change together — a level-up pays skill points, which is two of the
     * four numbers on each row. Two packets would let the header render a
     * half-applied update.</p>
     *
     * <p>Animus is carried from the first version even though nothing feeds it
     * yet: {@code AnimusSystem} is post-1.0 (#25), so its numbers are the zeroes
     * sitting in {@code OreVaultTeamData} and its progress is always 0. Sending
     * the real stored values rather than omitting the tree keeps the Animus tab
     * honest — it shows what the server actually has — and means #25 wires up
     * without another protocol bump.</p>
     */
    public record SyncTeamProgress(TreeProgress resonance, TreeProgress animus)
            implements CustomPacketPayload {
        public static final Type<SyncTeamProgress> TYPE = new Type<>(id("sync_team_progress"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncTeamProgress> CODEC =
                StreamCodec.composite(
                        TreeProgress.CODEC, SyncTeamProgress::resonance,
                        TreeProgress.CODEC, SyncTeamProgress::animus,
                        SyncTeamProgress::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Server pushes what the Resonance tree tab needs to draw node states (§8).
     *
     * <p>Separate from {@link SyncTeamProgress} because the two change on wildly
     * different schedules: the header's numbers move on every orb, this moves
     * only on a purchase or a toggle. Folding ~40 entries into the packet that
     * fires per pickup would be the most-sent packet in the mod carrying the
     * least-changing data.</p>
     *
     * <p>Tiers are team state; active tradeoffs are per-player (§6.1), so this
     * packet is addressed to one player and never broadcast verbatim.</p>
     */
    public record SyncSkillTree(Map<String, Integer> resonanceTiers, List<String> activeTradeoffs)
            implements CustomPacketPayload {
        public static final Type<SyncSkillTree> TYPE = new Type<>(id("sync_skill_tree"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncSkillTree> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(
                                HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                        SyncSkillTree::resonanceTiers,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                        SyncSkillTree::activeTradeoffs,
                        SyncSkillTree::new);

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
        registrar.playToClient(SyncSkillTree.TYPE, SyncSkillTree.CODEC, ModNetwork::onClientPayload);
        registrar.playToClient(ResetVoteStatus.TYPE, ResetVoteStatus.CODEC, ModNetwork::onClientPayload);
    }

    // ----- pushing progress to clients -----

    /**
     * Sends one player their own team's progress.
     *
     * <p>Used on login, where {@link #syncTeam} cannot be trusted: FTB Teams may
     * not have filed the player under a team yet, and this path must work for
     * the player who just connected.</p>
     */
    public static void syncTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        UUID teamId = TeamHelper.getTeamId(player);
        PacketDistributor.sendToPlayer(player, progressOf(server, teamId));
        PacketDistributor.sendToPlayer(player, skillTreeOf(server, teamId, player));
    }

    /**
     * Sends one player the tree state behind the Tome's node graph.
     *
     * <p>Per-player rather than per-team because the active tradeoff set is
     * per-player (§6.1), so there is no one payload the whole team could
     * share.</p>
     */
    public static void syncSkillTreeTo(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, skillTreeOf(server, TeamHelper.getTeamId(player), player));
    }

    /** Sends every online member the tree state, each with their own toggles. */
    public static void syncSkillTree(MinecraftServer server, UUID teamId) {
        for (ServerPlayer member : onlineMembers(server, teamId)) {
            PacketDistributor.sendToPlayer(member, skillTreeOf(server, teamId, member));
        }
    }

    private static SyncSkillTree skillTreeOf(MinecraftServer server, UUID teamId, ServerPlayer player) {
        OreVaultTeamData data = OreVaultTeamData.getOrCreate(server.overworld(), teamId);
        PlayerStats stats = data.getOrCreatePlayerStats(player.getUUID());
        return new SyncSkillTree(
                Map.copyOf(data.resonanceTree().getUnlockedTiers()),
                List.copyOf(stats.getActiveTradeoffs()));
    }

    /**
     * Pushes a team's progress to every online member.
     *
     * <p>Called whenever something moves one of the eight numbers the Tome's
     * header draws — a Resonance gain, a purchase, a refund. The Tome is drawn
     * every frame from the last thing the client was told, so a change that does
     * not push here is a header that silently goes stale while it is open.</p>
     */
    public static void syncTeam(MinecraftServer server, UUID teamId) {
        SyncTeamProgress payload = progressOf(server, teamId);
        for (ServerPlayer member : onlineMembers(server, teamId)) {
            PacketDistributor.sendToPlayer(member, payload);
        }
    }

    /**
     * Online members of a team, with the solo fallback.
     *
     * <p>Same hole as {@code TeamHelper.teamSize} (#128): before FTB Teams files
     * a player, {@code getTeamId} hands back the player's own UUID, which is not
     * a team id and matches no team — so the member lookup comes back empty for
     * someone standing right there with the Tome open.</p>
     */
    private static Collection<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        Collection<ServerPlayer> members = TeamHelper.getOnlineTeamMembers(teamId);
        if (!members.isEmpty()) {
            return members;
        }
        ServerPlayer solo = server.getPlayerList().getPlayer(teamId);
        return solo == null ? List.of() : List.of(solo);
    }

    /** Reads both trees out of SavedData. Main thread only. */
    private static SyncTeamProgress progressOf(MinecraftServer server, UUID teamId) {
        OreVaultTeamData data = OreVaultTeamData.getOrCreate(server.overworld(), teamId);
        LevelCurve curve = ResonanceSystem.curve();
        TreeProgress resonance = new TreeProgress(
                data.getResonancePool(),
                ResonanceSystem.levelOf(data, curve),
                data.getResonanceSkillPoints(),
                (float) ResonanceSystem.progressToNextLevel(data, curve));
        // No curve for Animus until #25, so no progress to report. The level and
        // point counts are the stored ones, which are the truth: zero.
        TreeProgress animus = new TreeProgress(
                data.getAnimusPool(), data.getAnimusLevel(), data.getAnimusSkillPoints(), 0.0F);
        return new SyncTeamProgress(resonance, animus);
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
                // The client thought this was buyable and it was not, so its copy
                // of the tree is wrong. Correcting it costs one packet.
                syncSkillTreeTo(player);
                return;
            }

            data.addResonanceSkillPoints(-cost);
            data.setDirty();
            syncTeam(overworld.getServer(), teamId);
            syncSkillTree(overworld.getServer(), teamId);
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
            // Sent whether or not the toggle was allowed: a refused toggle means
            // the client drew a state the server does not agree with, and the
            // correction is exactly what it needs.
            syncSkillTreeTo(player);
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
