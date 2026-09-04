package com.orevault.orevault.client;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.network.ModNetwork.ResetVoteStatus;
import com.orevault.orevault.network.ModNetwork.SyncTeamProgress;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handling for every clientbound Ore Vault packet (§8), and the
 * client's copy of the state they carry.
 *
 * <p>The state lives here rather than in a class of its own because this is the
 * only thing that ever writes it. A screen reading a value it cannot have
 * written is easy to reason about; two writers would not be.</p>
 *
 * <h2>Why the client keeps a copy at all</h2>
 *
 * <p>The Tome is opened by a keypress and drawn every frame. It cannot ask the
 * server for the pool while rendering, so the server pushes on change and the
 * client draws from the last thing it was told. {@link #teamProgress()} is
 * {@code null} until the first sync arrives, which is a real state the screens
 * have to render — "connecting" rather than "level 0", because a fresh join
 * shows zero for a moment before the truth lands and a team that has earned
 * 40,000 Resonance should never flash 0.</p>
 *
 * <h2>Client-only, and reached through a hook</h2>
 *
 * <p>{@code ModNetwork} registers the payloads from common code and cannot name
 * this class: a common-path reference to anything under {@code client/} kills a
 * dedicated server at class-load, and no unit test in this tree catches it. So
 * {@link OreVaultClient} installs {@link #handle} as a callback at startup, and
 * common code holds nothing but a functional interface.</p>
 */
public final class ClientPacketHandlers {

    private static volatile @Nullable SyncTeamProgress teamProgress;
    private static volatile @Nullable ResetVoteStatus resetVote;

    private ClientPacketHandlers() {
    }

    // ----- readouts for the Tome screens ([34]-[35]) -----

    /** Last team progress the server sent, or {@code null} before the first sync. */
    public static @Nullable SyncTeamProgress teamProgress() {
        return teamProgress;
    }

    /** State of a running reset vote, or {@code null} when no vote is open. */
    public static @Nullable ResetVoteStatus resetVote() {
        return resetVote;
    }

    /**
     * Drops everything the client was told.
     *
     * <p>Called on disconnect. Without it, the values from the last server stay
     * behind and the next world's Tome opens showing another team's Resonance
     * until its first sync overwrites them — briefly, but wrongly, and in
     * singleplayer it would look like the save had loaded the wrong data.</p>
     */
    public static void clear() {
        teamProgress = null;
        resetVote = null;
    }

    // ----- dispatch -----

    /**
     * Routes one clientbound payload. Installed on {@code ModNetwork} by
     * {@link OreVaultClient}; never called on a dedicated server.
     *
     * <p>Writes happen inside {@code enqueueWork} so the render thread never
     * observes a half-applied update.</p>
     */
    public static void handle(CustomPacketPayload payload, IPayloadContext context) {
        switch (payload) {
            case ModNetwork.SyncTeamProgress sync -> context.enqueueWork(() -> {
                teamProgress = sync;
                OreVault.LOGGER.debug("Team progress synced: level {} pool {} ({} unspent)",
                        sync.level(), sync.pool(), sync.unspentPoints());
            });
            case ModNetwork.ResetVoteStatus vote -> context.enqueueWork(() -> resetVote = vote);
            default -> OreVault.LOGGER.warn("Unhandled clientbound payload {}", payload.type().id());
        }
    }
}
