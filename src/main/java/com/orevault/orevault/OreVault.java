package com.orevault.orevault;

import com.mojang.logging.LogUtils;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.client.VaultOrbRenderers;
import com.orevault.orevault.client.VaultPortalColors;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.debug.VaultDiag;
import com.orevault.orevault.event.FtbEvents;
import com.orevault.orevault.entity.ModEntities;
import com.orevault.orevault.event.PortalEvents;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.resonance.ResonanceSystem;
import com.orevault.orevault.worldgen.VaultDimensions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
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

        // Portal protection (creative-mode break cancelling).
        NeoForge.EVENT_BUS.register(PortalEvents.class);

        // Resonance pool, levels and skill-point awards; computes the §4.3 curve at server start.
        NeoForge.EVENT_BUS.register(ResonanceSystem.class);

        // Playtest diagnostics (#82): block-break instrumentation + /orevault diag.
        NeoForge.EVENT_BUS.register(VaultDiag.class);

        // Server-side config (§10).
        modContainer.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);

        // Block registry ([16]; extended by [17], [27], [29]).
        ModBlocks.BLOCKS.register(modEventBus);

        // Item registry ([19]: igniter tiers + frame item form; [33], [65] later)
        // plus the Ore Vault creative tab (items are invisible in creative/JEI without tab membership).
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);

        // Entity registry ([21]: Resonance orb; the Animus orb lands post-1.0).
        ModEntities.ENTITY_TYPES.register(modEventBus);

        // Client-side rendering. Both must stay behind the dist check: a
        // common-path reference to a client class kills a dedicated server at
        // class-load, and no unit test here would catch it.
        if (FMLEnvironment.getDist().isClient()) {
            VaultPortalColors.register(modEventBus);
            VaultOrbRenderers.register(modEventBus);
        }

        // Registrations are added incrementally in later tasks:
        //   [32] network.
    }
}
