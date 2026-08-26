package com.orevault.orevault.client;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.entity.ModEntities;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = OreVault.MODID, dist = Dist.CLIENT)
public class OreVaultClient {
    public OreVaultClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerRenderers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RESONANCE_ORB.get(),
                ctx -> new VaultOrbRenderer(ctx,
                        Identifier.fromNamespaceAndPath(OreVault.MODID, "textures/entity/resonance_orb.png"),
                        0.25F, 0.85F, 1.0F));
        event.registerEntityRenderer(ModEntities.ANIMUS_ORB.get(),
                ctx -> new VaultOrbRenderer(ctx,
                        Identifier.fromNamespaceAndPath(OreVault.MODID, "textures/entity/animus_orb.png"),
                        1.0F, 0.25F, 0.25F));
    }
}
