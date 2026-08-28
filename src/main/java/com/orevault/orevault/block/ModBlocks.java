package com.orevault.orevault.block;

import com.orevault.orevault.OreVault;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central block registry for Ore Vault. The single {@link #BLOCKS} register is
 * extended in place by later tasks ([17] portal, [27] disturbed zone, [29]
 * anchor).
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OreVault.MODID);

    /** Portal frame block (§3.2). */
    public static final DeferredHolder<Block, VaultFrameBlock> VAULT_FRAME =
            BLOCKS.register("vault_frame", () -> new VaultFrameBlock());

    /** Portal interior block (§3.2). */
    public static final DeferredHolder<Block, VaultPortalBlock> VAULT_PORTAL =
            BLOCKS.register("vault_portal", () -> new VaultPortalBlock());

    private ModBlocks() {
    }
}
