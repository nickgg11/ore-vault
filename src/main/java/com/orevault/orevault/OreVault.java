package com.orevault.orevault;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.config.OreVaultServerConfig;
import com.orevault.orevault.entity.ModEntities;
import com.orevault.orevault.event.FtbEvents;
import com.orevault.orevault.event.ServerEvents;
import com.orevault.orevault.item.ModItems;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.worldgen.VaultChunkGenerator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(OreVault.MODID)
public class OreVault {
    public static final String MODID = "orevault";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, MODID);

    public OreVault(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Ore Vault initialising for Minecraft 26.1 / NeoForge...");

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        CHUNK_GENERATORS.register("vault", () -> VaultChunkGenerator.CODEC);
        CHUNK_GENERATORS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, OreVaultServerConfig.SPEC);
        modEventBus.addListener(OreVaultServerConfig::onLoad);

        modEventBus.addListener(ModNetwork::register);
        modEventBus.addListener(com.orevault.orevault.chunk.VaultChunkLoader::registerControllers);

        NeoForge.EVENT_BUS.register(FtbEvents.class);
        NeoForge.EVENT_BUS.register(ServerEvents.class);
        if (com.orevault.orevault.skill.SoftDeps.isUltimineLoaded()) {
            LOGGER.info("Ore Vault: FTB Ultimine detected — enabling Ultimine integration");
            com.orevault.orevault.integration.FtbUltimineIntegration.register(NeoForge.EVENT_BUS);
        }
        if (com.orevault.orevault.skill.SoftDeps.isMekanismLoaded()) {
            LOGGER.info("Ore Vault: Mekanism detected — enabling ore processing tiers");
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
