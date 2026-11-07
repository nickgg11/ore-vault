package com.orevault.orevault.vault;

import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Planted violation of named rule 7: restore called from outside the root start listener. See ArchitectureFixtureTest. */
final class PlantedTeamEventRestores {
    void planted(MinecraftServer server) {
        VaultDimensions.restoreOne(server, UUID.randomUUID());
        VaultDimensions.restoreAll(server, List.of());
    }
}
