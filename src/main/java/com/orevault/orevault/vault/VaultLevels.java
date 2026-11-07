package com.orevault.orevault.vault;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The one code that touches the server's world map (§11 Dimension-per-Team Registration, ADR 0002). ArchUnit's named rule
 * 7 holds every other class away from {@code forgeGetWorldMap()} and {@code markWorldsDirty()}, and only
 * {@link VaultDimensions} calls {@link #register}.
 *
 * <p>Both server methods are deprecated NeoForge internals with no supported replacement, so the dimension path is
 * re-tested on every NeoForge bump (ADR 0002). Removal is not written: no level is removed while the server runs until the
 * level-unload spike (E3-S11, #245) says it is safe (S19).
 */
public final class VaultLevels {
    private VaultLevels() {
    }

    /**
     * Puts a level in the world map and marks the map dirty. The server ticks levels from a cached array that only
     * {@code markWorldsDirty()} rebuilds, so a level added after the first tick without it sits in the map and never ticks:
     * placing a block works, breaking one never finishes, and a relog seems to fix it (old #82, #89). Server thread only.
     */
    @SuppressWarnings("deprecation")
    static void register(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
    }
}
