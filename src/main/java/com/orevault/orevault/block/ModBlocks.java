package com.orevault.orevault.block;

import com.orevault.orevault.OreVault;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central block registry for Ore Vault. The single {@link #BLOCKS} register is
 * extended in place by later tasks ([27] disturbed zone, [29] anchor).
 *
 * <p>Blocks are registered via {@code registerBlock}, which sets the registry
 * id on the {@link BlockBehaviour.Properties} before construction — required
 * since 26.1 (blocks constructed without an id throw
 * {@code NullPointerException: Block id not set} and roll back the whole
 * registry event).</p>
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MODID);

    /** Portal frame block (§3.2). */
    public static final DeferredBlock<VaultFrameBlock> VAULT_FRAME = BLOCKS.registerBlock(
            "vault_frame",
            VaultFrameBlock::new,
            properties -> properties
                    .strength(50.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
    );

    /** Portal interior block (§3.2). */
    public static final DeferredBlock<VaultPortalBlock> VAULT_PORTAL = BLOCKS.registerBlock(
            "vault_portal",
            VaultPortalBlock::new,
            properties -> properties
                    .noCollision()
                    .strength(-1.0F, 3600000.0F)
                    .lightLevel(state -> 11)
    );

    private ModBlocks() {
    }
}
