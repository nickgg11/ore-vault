package com.orevault.orevault.vault;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.core.signet.SignetTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The Ore Vault Portal (§3.2), one block for each signet tier. Its 4 px shape is for the outline; it has no collision,
 * and the travel trigger stays the full cell, the default {@code getEntityInsideCollisionShape}.
 *
 * <p>A block with no collision holds fluid unless it says otherwise, and vanilla names only its own portal, so water would
 * wash this one away. {@link LiquidBlockContainer} stops flowing water and {@link #canBeReplaced(BlockState, Fluid)} stops
 * a bucket or dispenser.
 *
 * <p>Where the charge leads is {@link VaultTravel}'s: into the player's team's Vault from anywhere else, and home from a
 * Vault. Null, which vanilla reads as nowhere to go, for a fake player or a Vault that cannot be opened.
 */
public final class VaultPortalBlock extends Block implements Portal, LiquidBlockContainer {
    /** §3.2: four seconds. */
    private static final int TRANSITION_TICKS = 80;

    private final SignetTier tier;
    private final MapCodec<VaultPortalBlock> codec;

    public VaultPortalBlock(Properties properties, SignetTier tier) {
        super(properties);
        this.tier = tier;
        codec = simpleCodec(copied -> new VaultPortalBlock(copied, tier));
        registerDefaultState(stateDefinition.any().setValue(VaultOpenings.AXIS, Direction.Axis.X));
    }

    /** The rank of the signet that opened this portal, from 1. */
    public int tier() {
        return tier.rank();
    }

    @Override
    protected MapCodec<VaultPortalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VaultOpenings.AXIS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return VaultOpenings.SHAPES.get(state.getValue(VaultOpenings.AXIS));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        VaultOpenings.watchNeighbour(state, ticks, pos, directionToNeighbour, neighbourState);
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VaultOpenings.dissolveIfBroken(level, pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return VaultOpenings.rotated(state, rotation);
    }

    /** Players only (§3.2), on both sides: the client's own processor is what draws the overlay (old #81). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (entity instanceof Player && entity.canUsePortal(false)) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return TRANSITION_TICKS;
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        return VaultTravel.portalDestination(currentLevel, entity, portalEntryPos);
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }
}
