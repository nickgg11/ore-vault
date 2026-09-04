package com.orevault.orevault;

import com.mojang.logging.LogUtils;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.debug.VaultDiag;
import com.orevault.orevault.event.DropPipeline;
import com.orevault.orevault.event.FtbEvents;
import com.orevault.orevault.entity.ModEntities;
import com.orevault.orevault.event.PortalEvents;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.resonance.ResonanceSystem;
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

        // Portal protection (creative-mode break cancelling).
        NeoForge.EVENT_BUS.register(PortalEvents.class);

        // Resonance pool, levels and skill-point awards; computes the §4.3 curve at server start.
        NeoForge.EVENT_BUS.register(ResonanceSystem.class);

        // The single BlockDropsEvent listener (#92): runs the node stages in order,
        // then pays the break's Resonance (#26). No other class listens for drops.
        NeoForge.EVENT_BUS.register(DropPipeline.class);

        // Playtest instrumentation: /orevault diag and /orevault testore, plus the
        // Resonance pickup readout. Both gated on [debug] in config (#120).
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

        // Everything client-side lives in OreVaultClient, a @Mod(dist = CLIENT)
        // entrypoint the JVM never loads on a dedicated server ([39]).

        // Network channel ([32]): mod event bus, not the game bus — a payload
        // listener on the wrong bus never fires and every packet becomes an
        // unknown-channel disconnect.
        modEventBus.addListener(ModNetwork::register);
    }
}
