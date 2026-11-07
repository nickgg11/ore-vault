package com.orevault.orevault.client;

import com.orevault.orevault.core.frame.FrameLight;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import com.orevault.orevault.vault.VaultOpenings;
import com.orevault.orevault.vault.VaultPortalBlock;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The colour of a frame's strands (§3.2 Frame colour), read from the blocks beside it each time the section is drawn.
 * This runs on the section build thread and reads only the {@code level} it is given. {@code relevantProperties()} stays
 * empty, because no frame property affects it: a portal's own change redraws the blocks around it (ADR 0010).
 */
final class VaultFrameTint implements BlockTintSource {
    private static final int UNTINTED = 0xFFFFFFFF;

    @Override
    public int color(BlockState state) {
        return FrameLight.DORMANT;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        int tier = FrameLight.tierAround(pos.getX(), pos.getY(), pos.getZ(),
                (x, y, z, axis) -> tierAt(level.getBlockState(new BlockPos(x, y, z)), axis));
        return FrameLight.runeColor(tier);
    }

    /** The default is {@link #colorInWorld}, and {@code TerrainParticle} multiplies by it: breaking stone is stone. */
    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return UNTINTED;
    }

    private static int tierAt(BlockState state, Axis axis) {
        Direction.Axis wanted = axis == Axis.X ? Direction.Axis.X : Direction.Axis.Z;
        return state.getBlock() instanceof VaultPortalBlock portal && state.getValue(VaultOpenings.AXIS) == wanted
                ? portal.tier()
                : 0;
    }
}
