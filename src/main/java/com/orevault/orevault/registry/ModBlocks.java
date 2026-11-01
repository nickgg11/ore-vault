package com.orevault.orevault.registry;

import com.orevault.orevault.OreVault;
import com.orevault.orevault.vault.VaultFrameBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations. {@code registerBlock} sets the id on the properties; without it the block throws inside
 * {@code RegisterEvent}, the registry rolls back and other mods log "unbound value" errors (old #17).
 */
public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MOD_ID);

    public static final DeferredBlock<VaultFrameBlock> VAULT_FRAME = BLOCKS.registerBlock("vault_frame", VaultFrameBlock::new,
            properties -> properties
                    .mapColor(MapColor.STONE)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
