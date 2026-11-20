package com.orevault.orevault;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.orevault.orevault.config.ConfigReload;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.core.ore.Overrides;
import com.orevault.orevault.data.GenSnapshots;
import com.orevault.orevault.data.PlayerAttachment;
import com.orevault.orevault.debug.DebugCommands;
import com.orevault.orevault.debug.VaultTickLog;
import com.orevault.orevault.ore.OreClassifier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModCreativeTabs;
import com.orevault.orevault.registry.ModItems;
import com.orevault.orevault.registry.ModSounds;
import com.orevault.orevault.team.TeamHelper;
import com.orevault.orevault.vault.VaultDimensions;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPlayerEvents;
import com.orevault.orevault.vault.VaultTeamEvents;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's entry point. The constructor is the only place the mod event bus is handed over, so registries
 * and lifecycle listeners are wired here, and gameplay listeners go on {@code NeoForge.EVENT_BUS}.
 */
@Mod(OreVault.MOD_ID)
public final class OreVault {
    public static final String MOD_ID = "orevault";

    private static final Logger LOGGER = LoggerFactory.getLogger(OreVault.class);

    public OreVault(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        PlayerAttachment.register(modEventBus);
        TeamHelper.register();
        VaultTeamEvents.register();
        VaultPlayerEvents.register();
        VaultTickLog.register(OreVaultServerConfig::enableDebugCommands);
        container.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);
        modEventBus.addListener(ConfigReload::onReloading);
        NeoForge.EVENT_BUS.addListener(OreVault::registerCommands);
        NeoForge.EVENT_BUS.addListener(VaultOpenings::refuseBreak);
        NeoForge.EVENT_BUS.addListener(OreVault::onServerStarted);
        NeoForge.EVENT_BUS.addListener(OreVault::onServerStopped);
    }

    /** The root carries no guard: everything that is not a debug tool lives under it too, whatever the flag says (§10). */
    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MOD_ID);
        DebugCommands.attach(root, OreVaultServerConfig::enableDebugCommands);
        event.getDispatcher().register(root);
    }

    /**
     * The start of the server, in ADR 0002's order. It returns before the tick loop runs, so before any login is placed. The
     * steps before the restore join here with their stories: ADR 0007's start-of-server pass (E11), which hands the restore
     * the resets it finished; the ore classifier (E4-S2, here); the pending generation tradeoffs (E9). Each comes before the
     * restore, because the snapshot the restore fills reads what they leave: the classifier publishes its table before any
     * Vault level is registered, so no Vault chunk can generate before it (ADR 0009 "Publication").
     */
    private static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        classifyOres(server);
        VaultDimensions.restoreAll(server, List.of());
    }

    /**
     * A failure here must not stop the restore, or a player who logged out inside a Vault reloads in the Overworld sky
     * (rule 2), so it is logged and nothing is published; S3's generator refuses to build without a table.
     */
    private static void classifyOres(MinecraftServer server) {
        try {
            Overrides overrides = OreClassifier.readOverrides(server.registryAccess(),
                    OreVaultServerConfig.oreClassificationOverrides());
            OreClassifier.publish(OreClassifier.classifyServer(server, overrides));
        } catch (RuntimeException e) {
            LOGGER.error("The ore classifier failed, so no ore table is published for this session", e);
        }
    }

    /** A second world in one client must not find this one's entries under the same personal team id (ADR 0002). */
    private static void onServerStopped(ServerStoppedEvent event) {
        VaultDimensions.clear();
        GenSnapshots.clear();
        OreClassifier.clear();
    }
}
