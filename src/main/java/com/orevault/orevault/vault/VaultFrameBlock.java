package com.orevault.orevault.vault;

import com.mojang.serialization.MapCodec;
import com.orevault.orevault.core.frame.FrameCorner;
import com.orevault.orevault.core.frame.FrameNeighbours;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * §3.2. Six booleans record which neighbours are Vault Frames, and only Vault Frames, so each face can pick its
 * texture ({@code FrameNeighbours}). They are drawing state: anything asking whether a block belongs to a frame
 * tests the block, not these values.
 *
 * <p>The 12 states that round off an outer corner ({@code FrameCorner}) take its stepped shape: for the outline, for
 * collision through the default {@code getCollisionShape}, and for occlusion through the default
 * {@code getOcclusionShape}. That last one stops a solid neighbour culling its face behind the wedge. Stair-style
 * light handling lets light into the wedge but not through the arm sides, so the steps are not drawn black.
 */
public final class VaultFrameBlock extends Block {
    private static final MapCodec<VaultFrameBlock> CODEC = simpleCodec(VaultFrameBlock::new);

    private final Function<BlockState, VoxelShape> shapes;

    public VaultFrameBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), false);
        }
        registerDefaultState(state);
        shapes = getShapeForEachState(VaultFrameBlock::shapeOf);
    }

    private static VoxelShape shapeOf(BlockState state) {
        return FrameCorner.of(neighbours(state)).map(VaultFrameBlock::cornerShape).orElseGet(Shapes::block);
    }

    private static VoxelShape cornerShape(FrameCorner corner) {
        VoxelShape shape = Shapes.empty();
        for (FrameCorner.Element element : corner.elements()) {
            FrameCorner.Box box = element.box();
            shape = Shapes.or(shape, box(box.fromX(), box.fromY(), box.fromZ(), box.toX(), box.toY(), box.toZ()));
        }
        return shape;
    }

    private static FrameNeighbours neighbours(BlockState state) {
        return new FrameNeighbours(connected(state, Direction.NORTH), connected(state, Direction.EAST),
                connected(state, Direction.SOUTH), connected(state, Direction.WEST), connected(state, Direction.UP),
                connected(state, Direction.DOWN));
    }

    private static boolean connected(BlockState state, Direction direction) {
        return state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction));
    }

    @Override
    protected MapCodec<VaultFrameBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PipeBlock.PROPERTY_BY_DIRECTION.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction),
                    level.getBlockState(pos.relative(direction)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(directionToNeighbour), neighbourState.is(this));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** A rounded corner is not a full block, which by default would let a mob's path run through it. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
