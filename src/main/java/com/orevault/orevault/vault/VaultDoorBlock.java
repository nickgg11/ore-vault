package com.orevault.orevault.vault;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.core.frame.DoorPart;
import com.orevault.orevault.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The sealed door (§3.2): one block for every tier, because it has no tier until a signet picks one. It is not a portal.
 * A shared block would need its collision, light, {@code entityInside} and portal behaviour each switched on a state, for
 * one swap (ADR 0010). {@code part} holds {@link DoorPart#id()}, which is save data.
 */
public final class VaultDoorBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, DoorPart.MAX_ID);

    private static final MapCodec<VaultDoorBlock> CODEC = simpleCodec(VaultDoorBlock::new);

    public VaultDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(VaultOpenings.AXIS, Direction.Axis.X)
                .setValue(PART, DoorPart.FIELD_LEFT.id()));
    }

    static BlockState state(Direction.Axis axis, DoorPart part) {
        return ModBlocks.VAULT_DOOR.get().defaultBlockState()
                .setValue(VaultOpenings.AXIS, axis)
                .setValue(PART, part.id());
    }

    @Override
    protected MapCodec<VaultDoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VaultOpenings.AXIS, PART);
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

    /** It is a thin block by shape, which by default would let a mob's path run through a shut door. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
