package com.orevault.orevault.block;

import com.orevault.orevault.portal.VaultTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * Ore Vault Portal block: no collision, unbreakable, light 11, teleports players between
 * the Overworld and their team's Vault. Breaks to air when the frame is removed.
 */
public class VaultPortalBlock extends Block implements Portal {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final int PORTAL_COOLDOWN_TICKS = 80;

    public VaultPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        // Tier 4 Sovereign Vault Igniter: instant teleport, no cooldown.
        if (entity instanceof Player player && VaultTeleport.bestIgniterTier(player) >= 4) {
            return 0;
        }
        return PORTAL_COOLDOWN_TICKS;
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        return VaultTeleport.computeTransition(currentLevel, player, portalEntryPos);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        // Frame integrity: a horizontal neighbour that is neither frame nor portal breaks us.
        if (direction.getAxis().isHorizontal() && !neighborState.is(ModBlocks.VAULT_FRAME)
                && !neighborState.is(ModBlocks.VAULT_PORTAL)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.ENCHANT, x, y, z, 0.0, 0.0, 0.0);
        }
    }
}
