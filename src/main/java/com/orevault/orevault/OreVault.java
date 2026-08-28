package com.orevault.orevault;

import com.mojang.logging.LogUtils;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.event.FtbEvents;
import com.orevault.orevault.worldgen.VaultDimensions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(OreVault.MODID)
public class OreVault {
    public static final String MODID = "orevault";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OreVault(IEventBus modEventBus, ModContainer modContainer) {
        // FTB Teams lifecycle hooks (team created / deleted).
        NeoForge.EVENT_BUS.register(FtbEvents.class);

        // Per-team Vault dimension management (server start + team created).
        NeoForge.EVENT_BUS.register(VaultDimensions.class);

        // Server-side config (§10).
        modContainer.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);

        // Block registry ([16]; extended by [17], [27], [29]).
        ModBlocks.BLOCKS.register(modEventBus);

        // Registrations are added incrementally in later tasks:
        //   [19] items, [21] entities, [32] network.
    }
}
