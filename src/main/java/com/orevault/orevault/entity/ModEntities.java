package com.orevault.orevault.entity;

import com.orevault.orevault.OreVault;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(OreVault.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ResonanceOrbEntity>> RESONANCE_ORB =
            ENTITIES.registerEntityType("resonance_orb", ResonanceOrbEntity::new, MobCategory.MISC,
                    b -> b.sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1));

    public static final DeferredHolder<EntityType<?>, EntityType<AnimusOrbEntity>> ANIMUS_ORB =
            ENTITIES.registerEntityType("animus_orb", AnimusOrbEntity::new, MobCategory.MISC,
                    b -> b.sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1));

    private ModEntities() {
    }
}
