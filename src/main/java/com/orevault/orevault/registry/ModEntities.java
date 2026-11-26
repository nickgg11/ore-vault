package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.mining.ResonanceOrbEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity type registrations. Each id is save data from the day it ships ({@code registry/frozen_ids}), and each type needs a
 * renderer registered from the client entry: a type without one kills {@code runClient} at startup while a dedicated
 * server runs happily (old #116).
 */
public final class ModEntities {
    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(OreVault.MOD_ID);

    /**
     * Vanilla's orb size and tracking range, but {@code updateInterval} 2 rather than 20: the client only interpolates what
     * the server sends (§11), so positions must come often. Fire-immune because a lost orb is lost progression.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ResonanceOrbEntity>> RESONANCE_ORB =
            ENTITIES.registerEntityType("resonance_orb", ResonanceOrbEntity::new, MobCategory.MISC,
                    builder -> builder.noLootTable().sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(2).fireImmune());

    private ModEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
