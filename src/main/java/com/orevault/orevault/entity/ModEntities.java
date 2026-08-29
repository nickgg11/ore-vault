package com.orevault.orevault.entity;

import com.orevault.orevault.OreVault;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entity registry for Ore Vault. Extended in place by later tasks — the Animus
 * orb lands with the post-1.0 Animus epic, on the same {@link VaultOrbEntity}
 * base.
 */
public final class ModEntities {

    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(OreVault.MODID);

    /**
     * Resonance orb (§4.2).
     *
     * <p>{@code updateInterval} is 2 rather than vanilla's 20 for XP orbs.
     * Movement is server-authoritative here (see {@link VaultOrbEntity}), so the
     * client has nothing to predict with between updates and a 20-tick interval
     * would make orbs visibly jump toward the player.</p>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ResonanceOrbEntity>> RESONANCE_ORB =
            ENTITY_TYPES.registerEntityType(
                    "resonance_orb",
                    ResonanceOrbEntity::new,
                    MobCategory.MISC,
                    builder -> builder
                            .noLootTable()
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(6)
                            .updateInterval(2));

    private ModEntities() {
    }
}
