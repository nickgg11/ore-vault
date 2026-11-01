package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * The client entry point, never loaded on a dedicated server (rule 4). Registers the Config button on the mod's page:
 * NeoForge adds none by itself, and without one the config is reachable only by editing the file (old PR #121).
 * NeoForge greys a {@code SERVER} config out from the main menu, on a remote server and in a world open to LAN (§10).
 */
@Mod(value = OreVault.MOD_ID, dist = Dist.CLIENT)
public final class OreVaultClient {
    public OreVaultClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mod, parent) -> new ConfigurationScreen(mod, parent));
    }
}
