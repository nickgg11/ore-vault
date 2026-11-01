package com.orevault.orevault;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.orevault.orevault.config.ConfigReload;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.debug.DebugCommands;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModCreativeTabs;
import com.orevault.orevault.registry.ModItems;
import com.orevault.orevault.team.TeamHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The mod's entry point. The constructor is the only place the mod event bus is handed over, so registries
 * and lifecycle listeners are wired here, and gameplay listeners go on {@code NeoForge.EVENT_BUS}.
 */
@Mod(OreVault.MOD_ID)
public final class OreVault {
    public static final String MOD_ID = "orevault";

    public OreVault(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        TeamHelper.register();
        container.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);
        modEventBus.addListener(ConfigReload::onReloading);
        NeoForge.EVENT_BUS.addListener(OreVault::registerCommands);
    }

    /** The root carries no guard: everything that is not a debug tool lives under it too, whatever the flag says (§10). */
    private static void registerCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MOD_ID);
        DebugCommands.attach(root, OreVaultServerConfig::enableDebugCommands);
        event.getDispatcher().register(root);
    }
}
