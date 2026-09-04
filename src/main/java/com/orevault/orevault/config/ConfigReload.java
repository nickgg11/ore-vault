package com.orevault.orevault.config;

import com.orevault.orevault.OreVault;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Re-sends the command tree when this mod's server config is saved (§10).
 *
 * <h2>Why a config change has to push anything at all</h2>
 *
 * <p>{@code /orevault} is gated by a Brigadier {@code requires} predicate that
 * reads {@link OreVaultServerConfig#enableDebugCommands()}. The server evaluates
 * that predicate on every execution, so flipping the config takes effect
 * server-side immediately — the command genuinely runs the moment the setting is
 * on.</p>
 *
 * <p>What does <em>not</em> update on its own is the client's copy of the tree.
 * The server sends each player a filtered command tree on login and on a
 * permission change, and nothing else. So after enabling debug commands from the
 * mods menu the command works if typed out in full, but does not tab-complete
 * and shows red while typing — which reads exactly like a setting that did not
 * take, and is why this looked like it needed a world reload.</p>
 *
 * <p>{@code sendCommands} rebuilds the tree through {@code canUse}, so re-sending
 * it re-runs the predicate against the new value. One packet per online player,
 * only when this mod's config is saved.</p>
 *
 * <p>Registered on the <b>mod event bus</b>: {@link ModConfigEvent} is an
 * {@code IModBusEvent}, and on {@code NeoForge.EVENT_BUS} it would never
 * fire.</p>
 */
public final class ConfigReload {

    private ConfigReload() {
    }

    /** Pushes a fresh command tree to every online player. */
    public static void onReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != OreVaultServerConfig.SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            // Config reloaded with no world open. Nothing to tell, and the tree
            // every player gets on login is built from the new value anyway.
            return;
        }
        // The reload can arrive on the config file-watcher thread; the player
        // list and the dispatcher are main-thread state.
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
            OreVault.LOGGER.debug("Ore Vault config saved: command tree re-sent to {} player(s)",
                    server.getPlayerList().getPlayerCount());
        });
    }
}
