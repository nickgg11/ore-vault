package com.orevault.orevault.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Puts the Config button on Ore Vault's page in the mods list.
 *
 * <p>NeoForge does not add that button on its own — a mod has to register an
 * {@link IConfigScreenFactory} extension point or its config is reachable only
 * by editing the {@code .toml} on disk. NeoForge's own
 * {@link ConfigurationScreen} then renders the whole spec, taking each option's
 * label from the {@code .translation(...)} key set in
 * {@code OreVaultServerConfig} and its tooltip from that key plus
 * {@code .tooltip}.</p>
 *
 * <p><b>Client-only, and it has to stay that way.</b> {@code IConfigScreenFactory}
 * and {@code ConfigurationScreen} both live under
 * {@code neoforge.client.gui}, so a common-path reference here would crash a
 * dedicated server at class-load — which no unit test in this tree catches.
 * Registration goes through the {@code FMLEnvironment.getDist().isClient()} gate
 * in {@code OreVault}'s constructor, next to {@link VaultPortalColors}.</p>
 *
 * <h2>When the button is greyed out</h2>
 *
 * <p>Everything in the spec is a {@code SERVER} config, because every value in
 * it decides world generation or progression and the server is the only side
 * that may answer those. {@code ConfigurationScreen} therefore disables editing
 * in three cases, each with a tooltip saying why:</p>
 *
 * <ul>
 *   <li>from the main menu, with no world loaded — a server config is not
 *       loaded yet, so there is nothing to edit;</li>
 *   <li>connected to a remote server — the values live on that server;</li>
 *   <li>in a singleplayer world opened to LAN.</li>
 * </ul>
 *
 * <p>Singleplayer, in a world, not published: fully editable. That is the whole
 * of what a client can do with a server config, and moving values to a
 * {@code CLIENT} spec to widen it would mean the client deciding ore density,
 * which is not a trade worth making.</p>
 */
public final class VaultConfigScreen {

    private VaultConfigScreen() {
    }

    /** Registers the factory. Call only behind a client-dist check. */
    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (container, parent) -> new ConfigurationScreen(container, parent));
    }
}
