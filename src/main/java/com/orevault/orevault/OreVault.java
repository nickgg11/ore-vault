package com.orevault.orevault;

import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModCreativeTabs;
import com.orevault.orevault.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

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
    }
}
