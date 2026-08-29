package com.orevault.orevault.block;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.portal.VaultPortalShape;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Portal frame block (§3.2): hardness 1.5, blast resistance 6.0 (stone-speed
 * mining), requires
 * the correct tool to drop, metal sound, mineable with any pickaxe. Right-click
 * with an igniter triggers the portal shape scan
 * ({@code VaultIgniterItem} [19]); this block is otherwise inert.
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

    /**
     * Corner safety net (§3.2): a corner frame block is diagonal to every
     * portal block, so breaking one never reaches the portal through
     * {@code updateShape}. Any frame removal therefore scans the surroundings
     * for portal blocks whose frame is no longer valid and dissolves one —
     * the rest cascade via their own updateShape re-validation. (26.1 renamed
     * {@code onRemove} to {@code affectNeighborsAfterRemoval}; the replacement
     * state is read back from the level.)
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            VaultPortalShape.dissolveInvalidPortalsNear(level, pos);
        }
    }
}
