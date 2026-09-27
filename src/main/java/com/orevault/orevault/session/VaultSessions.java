package com.orevault.orevault.session;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.orevault.orevault.data.OreVaultTeamData;
import com.orevault.orevault.data.PlayerStats;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.worldgen.VaultDimensions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import org.jspecify.annotations.Nullable;

/**
 * Who is currently inside a Vault, and what they have done since they walked in (§6.1).
 *
 * <p>Owns every {@link VaultTrip}. Deep Habit, Long Delve and Apprentice's Ledger all read one, and
 * the §5.1 {@code timeInVaultTicks} stat is accumulated here because this is the only place that
 * knows a player is in a Vault on any given tick.</p>
 *
 * <h2>What counts as leaving</h2>
 *
 * <p>A player leaves a Vault by walking back through the portal, by dying, by an admin's
 * {@code /execute in}, or by logging out — and only the first of those goes through
 * {@code VaultTeleport}. So the boundary is watched through the dimension change itself rather than
 * the code that usually causes it, plus login and logout for the two cases where no dimension change
 * fires at all: a player who logged out inside a Vault reconnects straight into one.</p>
 *
 * <p>Trips are held in memory only. A relog is a new trip, which is deliberate (see
 * {@link VaultTrip}) and is why nothing here is {@code SavedData}. The map is touched from the
 * server thread alone — every event below is main-thread — so it is a plain {@code HashMap}; a
 * concurrent one would suggest it is safe to read from the generation executor, which it is not.</p>
 */
public final class VaultSessions {

    /** Wall-clock ticks between writes of {@code timeInVaultTicks}, so the save is not dirtied per tick. */
    private static final int STAT_FLUSH_INTERVAL_TICKS = 20;

    private static final Map<UUID, VaultTrip> TRIPS = new HashMap<>();

    private VaultSessions() {
    }

    // ----- readouts -----

    /**
     * The player's current trip, or {@code null} when they are not in a Vault.
     *
     * <p>Node effects treat {@code null} as "no bonus" rather than starting a trip of their own. A
     * trip created on a break would start mid-delve and pay Deep Habit for a thousand blocks the
     * player broke before the node could see them.</p>
     */
    public static @Nullable VaultTrip trip(ServerPlayer player) {
        return TRIPS.get(player.getUUID());
    }

    /** Number of trips in flight, for the diag readout. */
    public static int activeTripCount() {
        return TRIPS.size();
    }

    // ----- the boundary -----

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        endTrip(player);
        if (VaultDimensions.isVaultDimension(player.level())) {
            beginTrip(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && VaultDimensions.isVaultDimension(player.level())) {
            beginTrip(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            endTrip(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // A single-player client can start a second integrated server in the same JVM. Trips left
        // behind would be attributed to whoever next logged in with the same UUID, which on a
        // single-player world is the same person on a different save.
        TRIPS.clear();
    }

    // ----- the tick -----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        VaultTrip trip = TRIPS.get(player.getUUID());
        if (trip == null) {
            return;
        }
        // A trip can outlive the player's presence in the Vault by a tick or two if something moved
        // them without firing a dimension change. Ending it here rather than ticking on is the safe
        // asymmetry: a stale trip pays bonuses outside the Vault.
        if (!VaultDimensions.isVaultDimension(player.level())) {
            endTrip(player);
            return;
        }

        long gameTime = player.level().getGameTime();
        trip.onTick(gameTime);
        if (gameTime % STAT_FLUSH_INTERVAL_TICKS == 0) {
            flushTime(player, trip);
        }
    }

    // ----- trip lifecycle -----

    private static void beginTrip(ServerPlayer player) {
        TRIPS.put(player.getUUID(), new VaultTrip(player.level().getGameTime()));
    }

    private static void endTrip(ServerPlayer player) {
        VaultTrip trip = TRIPS.remove(player.getUUID());
        if (trip != null) {
            flushTime(player, trip);
        }
    }

    /**
     * Writes the trip's outstanding wall-clock ticks into the player's §5.1 stats.
     *
     * <p>Resolved through the team that owns the <em>player</em>, not the dimension: this also runs
     * on the way out, at which point the player is already standing in the overworld.</p>
     */
    private static void flushTime(ServerPlayer player, VaultTrip trip) {
        long owed = trip.drainUnrecordedTicks();
        if (owed <= 0) {
            return;
        }
        PlayerStats stats = statsFor(player);
        if (stats == null) {
            return;
        }
        stats.addTimeInVault(owed);
    }

    /**
     * The player's stats record, or {@code null} when the server is gone.
     *
     * <p>Marks the team's SavedData dirty, because every caller here is about to mutate the stats it
     * hands back.</p>
     */
    public static @Nullable PlayerStats statsFor(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        ServerLevel overworld = server.overworld();
        UUID teamId = TeamHelper.getTeamId(player);
        OreVaultTeamData data = OreVaultTeamData.getOrCreate(overworld, teamId);
        data.setDirty();
        return data.getOrCreatePlayerStats(player.getUUID());
    }
}
