package com.orevault.orevault.vault;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.VaultVariant;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds a variant's layer stack in the server's data packs (§3.1, ADR 0002 creation step 5), so a pack can reshape the
 * terrain. All the rules are {@link LayerStack}'s; a file that is missing or bad costs an ERROR and the built-in stack.
 */
public final class VaultLayerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(VaultLayerConfig.class);

    private VaultLayerConfig() {
    }

    /** {@code data/orevault/worldgen/vault_layers/<type>.json}. */
    public static Identifier file(VaultVariant variant) {
        return Identifier.fromNamespaceAndPath(OreVault.MOD_ID, "worldgen/vault_layers/" + variant.path() + ".json");
    }

    public static LayerStack load(MinecraftServer server, VaultVariant variant) {
        Identifier file = file(variant);
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            LOGGER.error("Layer stack {} is missing, using the built-in stack", file);
            return LayerStack.builtIn(variant);
        }
        try (Reader reader = resource.get().openAsReader()) {
            return LayerStack.fromJson(reader, variant, VaultLayerConfig::blockExists, LOGGER::error);
        } catch (IOException e) {
            LOGGER.error("Layer stack {} cannot be read, using the built-in stack", file, e);
            return LayerStack.builtIn(variant);
        }
    }

    private static boolean blockExists(String id) {
        Identifier block = Identifier.tryParse(id);
        return block != null && BuiltInRegistries.BLOCK.containsKey(block);
    }
}
