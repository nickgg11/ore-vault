package com.orevault.orevault.vault;

import net.minecraft.server.MinecraftServer;

/** Planted violation of named rule 7; see ArchitectureFixtureTest. */
final class PlantedVaultTouchesWorldMap {
    @SuppressWarnings("deprecation")
    void planted(MinecraftServer server) {
        server.markWorldsDirty();
        server.forgeGetWorldMap();
    }
}
