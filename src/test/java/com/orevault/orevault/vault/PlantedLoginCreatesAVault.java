package com.orevault.orevault.vault;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Planted violation of named rule 7: a login or team listener that makes a Vault. See ArchitectureFixtureTest. */
final class PlantedLoginCreatesAVault {
    void planted(MinecraftServer server) {
        VaultDimensions.findOrCreate(server, UUID.randomUUID());
    }
}
