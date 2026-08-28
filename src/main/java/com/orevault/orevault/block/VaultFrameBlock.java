package com.orevault.orevault.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Portal frame block (§3.2): hardness 50.0, blast resistance 1200.0, requires
 * the correct tool to drop, metal sound. Right-click-with-igniter behavior
 * lives in {@code VaultIgniterItem} ([19]); this block is inert.
 *
 * <p>Block properties are supplied by {@link ModBlocks} at registration time
 * (id-aware {@code registerBlock}); this class only defines behaviour.</p>
 */
public class VaultFrameBlock extends Block {

    public static final MapCodec<VaultFrameBlock> CODEC = simpleCodec(VaultFrameBlock::new);

    public VaultFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<VaultFrameBlock> codec() {
        return CODEC;
    }
}
