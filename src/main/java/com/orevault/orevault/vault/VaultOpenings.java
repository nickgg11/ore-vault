package com.orevault.orevault.vault;

import com.orevault.orevault.core.frame.DoorPart;
import com.orevault.orevault.core.frame.VaultPortalShape;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.frame.VaultPortalShape.View;
import com.orevault.orevault.core.signet.SignetTier;
import com.orevault.orevault.registry.ModBlocks;
import com.orevault.orevault.registry.ModTags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * What a sealed door and a portal do alike (§3.2, ADR 0010): sealing a ring, pressing a signet, dissolving and refusing
 * a creative break. All of it runs on the server thread and reads no {@code SavedData}, and the generator never reaches
 * it, because generation has no {@code onPlace} and nothing here listens for a chunk loading (rule 1).
 */
public final class VaultOpenings {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    /** The thickness of a nether portal, by axis. */
    static final Map<Direction.Axis, VoxelShape> SHAPES = Shapes.rotateHorizontalAxis(Block.column(4.0, 16.0, 0.0, 16.0));

    /** As vanilla fills a nether portal: the client hears of it, and the neighbours are not asked. */
    private static final int FILL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final int NEXT_TICK = 1;

    private VaultOpenings() {
    }

    /** The scan's view of a level, which never loads a chunk. */
    public static View view(Level level) {
        return View.loadedOnly(level::hasChunkAt, (x, y, z) -> kind(level.getBlockState(new BlockPos(x, y, z))));
    }

    private static Kind kind(BlockState state) {
        if (state.is(ModBlocks.VAULT_FRAME)) {
            return Kind.FRAME;
        }
        if (state.isAir()) {
            return Kind.AIR;
        }
        return state.is(ModTags.VAULT_OPENINGS) ? Kind.PORTAL : Kind.OTHER;
    }

    /**
     * Fills every ring this frame block completes with a door. The door is presentation only, so a ring that does not
     * seal still opens to a signet. All-air is asked of each ring as it is written, not when the scan found it: two
     * crossing rings can share a column, and {@code find} also accepts a ring with a live portal in it, which a corner
     * placed afterwards would otherwise cover with a door.
     */
    static void seal(ServerLevel level, BlockPos pos) {
        View view = view(level);
        for (VaultPortalShape shape : VaultPortalShape.findAll(view, pos.getX(), pos.getY(), pos.getZ())) {
            if (shape.cells().stream().allMatch(cell -> view.kindAt(cell.x(), cell.y(), cell.z()) == Kind.AIR)) {
                writeDoor(level, shape);
            }
        }
    }

    private static void writeDoor(ServerLevel level, VaultPortalShape shape) {
        for (Cell cell : shape.cells()) {
            DoorPart part = DoorPart.of(shape.column(cell), cell.y() - shape.minY(), shape.width(), shape.height());
            level.setBlock(pos(cell), VaultDoorBlock.state(minecraftAxis(shape.axis()), part), FILL_FLAGS);
        }
    }

    /** Whether a signet does anything on this block. */
    static boolean takesASignet(BlockState state) {
        return state.is(ModBlocks.VAULT_FRAME) || state.is(ModTags.VAULT_OPENINGS);
    }

    /**
     * Presses a signet into a frame, door or portal (§3.2 Pressing a Signet).
     *
     * @return false if it was refused, which leaves the world as it was
     */
    public static boolean press(ServerLevel level, BlockPos pos, SignetTier tier) {
        Optional<VaultPortalShape> found = shapeFor(level, pos, level.getBlockState(pos));
        if (found.isEmpty()) {
            return false;
        }
        VaultPortalShape shape = found.get();
        Direction.Axis axis = minecraftAxis(shape.axis());
        int highest = 0;
        for (Cell cell : shape.cells()) {
            BlockState state = level.getBlockState(pos(cell));
            // Two crossing rings share a cell, and the other ring's door is not this press's to replace.
            if (state.hasProperty(AXIS) && state.getValue(AXIS) != axis) {
                return false;
            }
            highest = Math.max(highest, state.getBlock() instanceof VaultPortalBlock portal ? portal.tier() : 0);
        }
        if (!tier.mayPress(highest)) {
            return false;
        }
        BlockState portal = ModBlocks.portal(tier).defaultBlockState().setValue(AXIS, axis);
        shape.cells().forEach(cell -> level.setBlock(pos(cell), portal, FILL_FLAGS));
        return true;
    }

    private static Optional<VaultPortalShape> shapeFor(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.VAULT_FRAME)) {
            return VaultPortalShape.find(view(level), pos.getX(), pos.getY(), pos.getZ());
        }
        return VaultPortalShape.around(view(level), scanAxis(state), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The door's and portal's half of {@code updateShape}, which has only a {@code LevelReader} to write with. A corner
     * never reaches this: it touches no cell by a side, which is why breaking one leaves the opening standing.
     */
    static void watchNeighbour(BlockState state, ScheduledTickAccess ticks, BlockPos pos, Direction toNeighbour,
            BlockState neighbour) {
        boolean behindOrInFront = toNeighbour.getAxis() != state.getValue(AXIS) && toNeighbour.getAxis().isHorizontal();
        if (!behindOrInFront && !neighbour.is(ModBlocks.VAULT_FRAME) && !neighbour.is(ModTags.VAULT_OPENINGS)) {
            ticks.scheduleTick(pos, state.getBlock(), NEXT_TICK);
        }
    }

    /**
     * Removes the whole door or portal if its opening is no longer valid. It collects first and then removes, with
     * {@code UPDATE_ALL} so nearby water and redstone hear it: the first build's cascade left cells standing (old PR
     * #108). The scanner accepts air, so a hole has to be looked for here. The collect reads the level directly, since a
     * synchronous load beats an unbreakable orphan.
     */
    static void dissolveIfBroken(ServerLevel level, BlockPos pos, BlockState state) {
        Optional<VaultPortalShape> shape = shapeFor(level, pos, state);
        if (shape.isPresent() && shape.get().cells().stream().allMatch(cell -> isOpening(level, pos(cell)))) {
            return;
        }
        Direction.Axis axis = state.getValue(AXIS);
        List<Cell> cells = VaultPortalShape.connected(scanAxis(state), cell(pos), cell -> sameAxisOpening(level, cell, axis));
        cells.forEach(cell -> level.setBlock(pos(cell), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
    }

    private static boolean isOpening(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(ModTags.VAULT_OPENINGS);
    }

    private static boolean sameAxisOpening(Level level, Cell cell, Direction.Axis axis) {
        BlockState state = level.getBlockState(pos(cell));
        return state.is(ModTags.VAULT_OPENINGS) && state.getValue(AXIS) == axis;
    }

    /** Hardness stops survival mining, and creative ignores it. Creative plus sneak is how a stuck cell is cleared (old #18). */
    public static void refuseBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (event.getState().is(ModTags.VAULT_OPENINGS) && !(player.getAbilities().instabuild && player.isShiftKeyDown())) {
            event.setCanceled(true);
        }
    }

    /** Turns with a structure, so a door or portal placed at a quarter turn is not left thin the wrong way. */
    static BlockState rotated(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    private static Axis scanAxis(BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.X ? Axis.X : Axis.Z;
    }

    private static Direction.Axis minecraftAxis(Axis axis) {
        return axis == Axis.X ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static BlockPos pos(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static Cell cell(BlockPos pos) {
        return new Cell(pos.getX(), pos.getY(), pos.getZ());
    }
}
