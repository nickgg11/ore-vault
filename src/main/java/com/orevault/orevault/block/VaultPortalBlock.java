package com.orevault.orevault.block;

import java.util.Map;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.portal.VaultTeleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Ore Vault portal block — the interior fill of a portal frame (§3.2).
 *
 * <ul>
 * <li>No collision, hardness -1.0 (unbreakable), blast resistance 3,600,000,
 * light level 11.</li>
 * <li>{@code HORIZONTAL_AXIS} state for orientation.</li>
 * <li>{@link #updateShape} breaks to air when the frame around it is damaged
 * (a neighbouring block is neither frame nor portal).</li>
 * <li>{@link #entityInside} triggers teleportation for players once
 * {@code VaultTeleport} lands in [20]; a documented stub until then.</li>
 * </ul>
 */
public class VaultPortalBlock extends Block {

    public static final MapCodec<VaultPortalBlock> CODEC = simpleCodec(VaultPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** Thin vertical slab in the centre of the block, rotated per axis (mirrors NetherPortalBlock). */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

    public VaultPortalBlock() {
        this(BlockBehaviour.Properties.of()
                .noCollision()
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 11));
    }

    public VaultPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public MapCodec<VaultPortalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    /**
     * Portal frame integrity check (§3.2): if a horizontal neighbour off the
     * portal plane is neither another portal block nor a Vault Frame block,
     * the portal dissolves to air.
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbour,
            BlockPos neighbourPos,
            BlockState neighbourState,
            RandomSource random
    ) {
        Direction.Axis updateAxis = directionToNeighbour.getAxis();
        Direction.Axis axis = state.getValue(AXIS);
        boolean samePlane = axis == updateAxis || !updateAxis.isHorizontal();
        return !samePlane && !neighbourState.is(this) && !neighbourState.is(ModBlocks.VAULT_FRAME)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    /**
     * Teleport trigger (§3.2): players only, server side, gated by the vanilla
     * portal cooldown. Routing lives in {@link VaultTeleport} ([20]).
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (player.isOnPortalCooldown()) {
            return;
        }
        VaultTeleport.handlePortal(player);
    }

    /** Unbreakable interior block: never pick-blockable (matches vanilla portal behaviour). */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
