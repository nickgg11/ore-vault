package com.orevault.orevault.vault;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Planted violation of named rule 7; see ArchitectureFixtureTest. */
final class PlantedVaultRegistersALevel {
    void planted(MinecraftServer server, ResourceKey<Level> key, ServerLevel level) {
        VaultLevels.register(server, key, level);
    }
}
