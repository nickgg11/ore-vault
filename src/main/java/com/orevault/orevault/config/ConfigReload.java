package com.orevault.orevault.config;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-sends the command tree when this mod's config changes (§10). The server evaluates a {@code requires} predicate
 * on every execution, so {@code enable_debug_commands} takes effect at once there, but the client's copy of the tree
 * is sent only at login and on a permission change. Without this the command runs when typed out in full and still
 * shows red and will not complete, which reads as a setting that needs a relog (old PR #133).
 *
 * <p>{@link ModConfigEvent} is a mod-bus event (rule 3).
 */
public final class ConfigReload {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigReload.class);

    private ConfigReload() {
    }

    public static void onReloading(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() != OreVaultServerConfig.SPEC || server == null) {
            return;
        }
        LOG.info("Ore Vault config changed, re-sending the command tree");
        resendOnServerThread(server, () -> server.getPlayerList().getPlayers(), server::getCommands,
                Commands::sendCommands);
    }

    /** The reload can arrive on the config file watcher's thread; the player list and the tree are the server's. */
    static <P, C> void resendOnServerThread(Executor serverThread, Supplier<? extends Collection<P>> online,
            Supplier<C> commands, BiConsumer<C, P> send) {
        serverThread.execute(() -> {
            C current = commands.get();
            online.get().forEach(player -> send.accept(current, player));
        });
    }
}
