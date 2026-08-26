package com.orevault.orevault.block;

import com.orevault.orevault.zone.DisturbedZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Disturbed Zone block: creates a spherical mob spawn zone when placed inside the Vault.
 * Craftable only after the Disturbed Zone Unlock node. Emits red boundary particles.
 */
public class DisturbedZoneBlock extends Block {
    public DisturbedZoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            DisturbedZoneManager.onZonePlaced(serverLevel, pos);
        }
    }

    // Block removal is detected lazily by DisturbedZoneManager's periodic sweep, which
    // unregisters any zone whose block is no longer present (26.1 removed Block#onRemove).

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5;
            level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y, z, 0.0, 0.02, 0.0);
        }
    }
}
