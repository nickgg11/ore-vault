package com.orevault.orevault.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.orevault.orevault.block.ModBlocks;
import com.orevault.orevault.block.VaultPortalBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Portal frame shape scanner (§3.2).
 *
 * <p>Scans outward from a clicked Vault Frame block in both horizontal
 * orientations (X, then Z). A valid frame is a rectangle whose four sides are
 * entirely Vault Frame blocks, with interior dimensions between 2×3 and 21×21,
 * and an interior made entirely of air or existing portal blocks.</p>
 *
 * <p>The geometry is pure and testable through {@link #find(View, BlockPos)}
 * — the {@link View} abstraction classifies positions without needing a real
 * {@link Level}, so JUnit tests can validate/reject frame layouts without a
 * running server. {@link #find(LevelReader, BlockPos)} adapts a real world to
 * the same logic, and {@link #fill(Level)} performs the block mutation.</p>
 */
public final class VaultPortalShape {

    /** Interior size limits (§3.2): 2×3 minimum, 21×21 maximum. */
    public static final int MIN_INTERIOR_WIDTH = 2;
    public static final int MAX_INTERIOR_WIDTH = 21;
    public static final int MIN_INTERIOR_HEIGHT = 3;
    public static final int MAX_INTERIOR_HEIGHT = 21;

    /** Frame extent limits: interior + the two frame columns/rows. */
    private static final int MIN_FRAME_WIDTH = MIN_INTERIOR_WIDTH + 2;
    private static final int MAX_FRAME_WIDTH = MAX_INTERIOR_WIDTH + 2;
    private static final int MIN_FRAME_HEIGHT = MIN_INTERIOR_HEIGHT + 2;
    private static final int MAX_FRAME_HEIGHT = MAX_INTERIOR_HEIGHT + 2;
    /** Max walk distance from the clicked block to a far edge of a valid frame. */
    private static final int MAX_STEPS = MAX_FRAME_WIDTH - 1;

    private static final Direction.Axis[] SCAN_ORDER = { Direction.Axis.X, Direction.Axis.Z };

    /** Block classification used by the pure geometry (no Level needed in tests). */
    public enum Kind {
        FRAME, PORTAL, AIR, OTHER
    }

    /** Position classifier; implemented by tests directly and by real levels via adapter. */
    @FunctionalInterface
    public interface View {
        Kind kindAt(int x, int y, int z);
    }

    private final BlockPos minCorner;
    private final int frameWidth;
    private final int frameHeight;
    private final Direction.Axis axis;

    private VaultPortalShape(BlockPos minCorner, int frameWidth, int frameHeight, Direction.Axis axis) {
        this.minCorner = minCorner;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.axis = axis;
    }

    /** Finds the frame containing {@code clicked} in a real level, if valid. */
    public static Optional<VaultPortalShape> find(LevelReader level, BlockPos clicked) {
        return find((x, y, z) -> classify(level.getBlockState(new BlockPos(x, y, z))), clicked);
    }

    /**
     * Pure geometry entry point: scans outward from {@code clicked} in both X
     * and Z orientations and returns the first valid frame, if any.
     */
    public static Optional<VaultPortalShape> find(View view, BlockPos clicked) {
        if (view.kindAt(clicked.getX(), clicked.getY(), clicked.getZ()) != Kind.FRAME) {
            return Optional.empty();
        }
        for (Direction.Axis axis : SCAN_ORDER) {
            Optional<VaultPortalShape> found = findAlong(view, clicked, axis);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<VaultPortalShape> findAlong(View view, BlockPos clicked, Direction.Axis axis) {
        int fixed = fixedCoord(clicked, axis);

        // Horizontal extent at the clicked row.
        int minH = horizCoord(clicked, axis) - walk(view, clicked, axis, -1);
        int maxH = horizCoord(clicked, axis) + walk(view, clicked, axis, 1);
        int width = maxH - minH + 1;

        int minY;
        int maxY;
        if (width >= MIN_FRAME_WIDTH) {
            // Clicked row is a full-width frame row (top or bottom): measure the
            // height along the edge column.
            if (width > MAX_FRAME_WIDTH) {
                return Optional.empty();
            }
            BlockPos edge = at(minH, clicked.getY(), fixed, axis);
            minY = clicked.getY() - walkVertical(view, edge, axis, -1);
            maxY = clicked.getY() + walkVertical(view, edge, axis, 1);
        } else {
            // Clicked block is on a side column: measure the height from the
            // column itself, then the width from the bottom row.
            minY = clicked.getY() - walkVertical(view, clicked, axis, -1);
            maxY = clicked.getY() + walkVertical(view, clicked, axis, 1);
            int height = maxY - minY + 1;
            if (height < MIN_FRAME_HEIGHT || height > MAX_FRAME_HEIGHT) {
                return Optional.empty();
            }
            BlockPos bottomEdge = at(horizCoord(clicked, axis), minY, fixed, axis);
            minH = horizCoord(clicked, axis) - walk(view, bottomEdge, axis, -1);
            maxH = horizCoord(clicked, axis) + walk(view, bottomEdge, axis, 1);
            width = maxH - minH + 1;
            if (width < MIN_FRAME_WIDTH || width > MAX_FRAME_WIDTH) {
                return Optional.empty();
            }
        }

        int height = maxY - minY + 1;
        if (height < MIN_FRAME_HEIGHT || height > MAX_FRAME_HEIGHT) {
            return Optional.empty();
        }

        if (!validateFrame(view, minH, maxH, minY, maxY, fixed, axis)) {
            return Optional.empty();
        }

        return Optional.of(new VaultPortalShape(at(minH, minY, fixed, axis), width, height, axis));
    }

    /**
     * True if {@code pos} (an existing portal block) still belongs to a
     * complete, valid frame. Used by {@code VaultPortalBlock#updateShape} so
     * that breaking any frame block — including a corner, which is diagonal
     * to every portal block — dissolves the whole portal.
     */
    public static boolean isValidFrameContaining(LevelReader level, BlockPos pos) {
        View view = (x, y, z) -> classify(level.getBlockState(new BlockPos(x, y, z)));
        for (Direction.Axis axis : SCAN_ORDER) {
            int fixed = fixedCoord(pos, axis);

            // Walk the interior run horizontally and vertically from the portal block.
            int minH = horizCoord(pos, axis) - walkWhileInterior(view, pos, axis, -1);
            int maxH = horizCoord(pos, axis) + walkWhileInterior(view, pos, axis, 1);
            int minY = pos.getY() - walkWhileInteriorVertical(view, pos, axis, -1);
            int maxY = pos.getY() + walkWhileInteriorVertical(view, pos, axis, 1);

            // Frame bounds sit one block outside the interior run; validateFrame re-checks the size limits.
            if (validateFrame(view, minH - 1, maxH + 1, minY - 1, maxY + 1, fixed, axis)) {
                return true;
            }
        }
        return false;
    }

    /** Steps while the cell is interior (air or portal), capped at the max interior size. */
    private static int walkWhileInterior(View view, BlockPos start, Direction.Axis axis, int step) {
        int steps = 0;
        int y = start.getY();
        int fixed = fixedCoord(start, axis);
        int h = horizCoord(start, axis) + step;
        while (steps < MAX_INTERIOR_WIDTH && isInterior(kindAt(view, h, y, fixed, axis))) {
            steps++;
            h += step;
        }
        return steps;
    }

    /** Steps vertically while the cell is interior (air or portal), capped at the max interior height. */
    private static int walkWhileInteriorVertical(View view, BlockPos start, Direction.Axis axis, int step) {
        int steps = 0;
        int h = horizCoord(start, axis);
        int fixed = fixedCoord(start, axis);
        int y = start.getY() + step;
        while (steps < MAX_INTERIOR_HEIGHT && isInterior(kindAt(view, h, y, fixed, axis))) {
            steps++;
            y += step;
        }
        return steps;
    }

    private static boolean isInterior(Kind kind) {
        return kind == Kind.AIR || kind == Kind.PORTAL;
    }

    /**
     * Verifies that {@code (minH..maxH) × (minY..maxY)} on the given plane is a
     * complete rectangular frame with a valid, empty interior.
     */
    private static boolean validateFrame(View view, int minH, int maxH, int minY, int maxY, int fixed, Direction.Axis axis) {
        int width = maxH - minH + 1;
        int height = maxY - minY + 1;
        if (width < MIN_FRAME_WIDTH || width > MAX_FRAME_WIDTH || height < MIN_FRAME_HEIGHT || height > MAX_FRAME_HEIGHT) {
            return false;
        }
        // All four sides must be solid frame.
        for (int h = minH; h <= maxH; h++) {
            if (kindAt(view, h, minY, fixed, axis) != Kind.FRAME || kindAt(view, h, maxY, fixed, axis) != Kind.FRAME) {
                return false;
            }
        }
        for (int y = minY; y <= maxY; y++) {
            if (kindAt(view, minH, y, fixed, axis) != Kind.FRAME || kindAt(view, maxH, y, fixed, axis) != Kind.FRAME) {
                return false;
            }
        }
        // Interior must be entirely air or existing portal blocks.
        for (int h = minH + 1; h < maxH; h++) {
            for (int y = minY + 1; y < maxY; y++) {
                Kind kind = kindAt(view, h, y, fixed, axis);
                if (kind != Kind.AIR && kind != Kind.PORTAL) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Steps of frame blocks along the horizontal axis, capped at {@link #MAX_STEPS}; returns {@code MAX_STEPS + 1} if over-long. */
    private static int walk(View view, BlockPos start, Direction.Axis axis, int step) {
        int steps = 0;
        int y = start.getY();
        int fixed = fixedCoord(start, axis);
        int h = horizCoord(start, axis) + step;
        while (steps < MAX_STEPS && kindAt(view, h, y, fixed, axis) == Kind.FRAME) {
            steps++;
            h += step;
        }
        return kindAt(view, h, y, fixed, axis) == Kind.FRAME ? MAX_STEPS + 1 : steps;
    }

    /** Steps of frame blocks vertically from {@code start} (exclusive), capped at {@link #MAX_STEPS}. */
    private static int walkVertical(View view, BlockPos start, Direction.Axis axis, int step) {
        int steps = 0;
        int h = horizCoord(start, axis);
        int fixed = fixedCoord(start, axis);
        int y = start.getY() + step;
        while (steps < MAX_STEPS && kindAt(view, h, y, fixed, axis) == Kind.FRAME) {
            steps++;
            y += step;
        }
        return kindAt(view, h, y, fixed, axis) == Kind.FRAME ? MAX_STEPS + 1 : steps;
    }

    /** Fills the interior with portal blocks oriented to the frame axis (§3.2). */
    public void fill(Level level) {
        BlockState portal = ModBlocks.VAULT_PORTAL.get().defaultBlockState().setValue(VaultPortalBlock.AXIS, axis);
        for (BlockPos pos : interiorPositions()) {
            level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Activation animation (§3.3): fills the interior progressively over
     * {@code totalTicks} ticks on the server thread, bottom row first, so the
     * portal visibly "opens". Optional portal-particle burst on the first
     * step (tier 2+ igniters).
     */
    public void fillAnimated(ServerLevel level, int totalTicks, boolean particleBurst) {
        List<BlockPos> positions = interiorPositions();
        if (positions.isEmpty()) {
            return;
        }
        if (particleBurst) {
            burst(level);
        }
        int rows = frameHeight - 2;
        if (totalTicks <= 1 || rows == 0) {
            fill(level);
            return;
        }
        BlockState portal = ModBlocks.VAULT_PORTAL.get().defaultBlockState().setValue(VaultPortalBlock.AXIS, axis);
        level.getServer().schedule(level.getServer().wrapRunnable(new FillTask(level, positions, rows, totalTicks, portal)));
    }

    private void burst(ServerLevel level) {
        RandomSource random = level.getRandom();
        int h = horizCoord(minCorner, axis);
        int fixed = fixedCoord(minCorner, axis);
        double centerH = h + (frameWidth - 1) / 2.0;
        double centerY = minCorner.getY() + (frameHeight - 1) / 2.0;
        for (int i = 0; i < 48; i++) {
            double x = centerH + (random.nextDouble() - 0.5) * frameWidth;
            double y = centerY + (random.nextDouble() - 0.5) * frameHeight;
            double z = fixed + (random.nextDouble() - 0.5) * 0.75;
            double vx = (random.nextFloat() - 0.5) * 0.3;
            double vy = random.nextFloat() * 0.4;
            double vz = (random.nextFloat() - 0.5) * 0.3;
            if (axis == Direction.Axis.Z) {
                double tmp = x;
                x = z;
                z = tmp;
                tmp = vx;
                vx = vz;
                vz = tmp;
            }
            level.addParticle(ParticleTypes.PORTAL, x, y, z, vx, vy, vz);
        }
    }

    /** Progressive fill task: runs once per server tick until every row is placed. */
    private static final class FillTask implements Runnable {
        private final ServerLevel level;
        private final List<BlockPos> positions;
        private final int rows;
        private final int totalTicks;
        private final int perRow;
        private final BlockState portal;
        private int nextRow;

        FillTask(ServerLevel level, List<BlockPos> positions, int rows, int totalTicks, BlockState portal) {
            this.level = level;
            this.positions = positions;
            this.rows = rows;
            this.totalTicks = totalTicks;
            this.perRow = positions.size() / rows;
            this.portal = portal;
        }

        @Override
        public void run() {
            int rowsThisTick = Math.max(1, (rows + totalTicks - 1) / totalTicks);
            int from = nextRow * perRow;
            int to = Math.min(positions.size(), from + rowsThisTick * perRow);
            for (int i = from; i < to; i++) {
                BlockPos pos = positions.get(i);
                if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(ModBlocks.VAULT_PORTAL)) {
                    level.setBlock(pos, portal, Block.UPDATE_CLIENTS);
                }
            }
            nextRow += rowsThisTick;
            if (nextRow < rows) {
                level.getServer().schedule(level.getServer().wrapRunnable(this));
            }
        }
    }

    /** All interior positions in row-major order (bottom row first) — used by the fill animation in [19]. */
    public List<BlockPos> interiorPositions() {
        List<BlockPos> positions = new ArrayList<>();
        int fixed = fixedCoord(minCorner, axis);
        int min = horizCoord(minCorner, axis);
        int max = min + frameWidth - 1;
        int minY = minCorner.getY();
        int maxY = minY + frameHeight - 1;
        for (int y = minY + 1; y < maxY; y++) {
            for (int h = min + 1; h < max; h++) {
                positions.add(at(h, y, fixed, axis));
            }
        }
        return positions;
    }

    // ----- accessors -----

    public BlockPos minCorner() {
        return minCorner;
    }

    /** Frame extent including both frame columns. */
    public int frameWidth() {
        return frameWidth;
    }

    /** Frame extent including both frame rows. */
    public int frameHeight() {
        return frameHeight;
    }

    public Direction.Axis axis() {
        return axis;
    }

    // ----- helpers -----

    private static Kind classify(BlockState state) {
        if (state.isAir()) {
            return Kind.AIR;
        }
        if (state.is(ModBlocks.VAULT_FRAME)) {
            return Kind.FRAME;
        }
        if (state.is(ModBlocks.VAULT_PORTAL)) {
            return Kind.PORTAL;
        }
        return Kind.OTHER;
    }

    private static Kind kindAt(View view, int h, int y, int fixed, Direction.Axis axis) {
        BlockPos pos = at(h, y, fixed, axis);
        return view.kindAt(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Builds the world position for a (horizontal, y) pair on the frame plane. */
    private static BlockPos at(int h, int y, int fixed, Direction.Axis axis) {
        return axis == Direction.Axis.X ? new BlockPos(h, y, fixed) : new BlockPos(fixed, y, h);
    }

    private static int horizCoord(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    private static int fixedCoord(BlockPos pos, Direction.Axis axis) {
        return axis == Direction.Axis.X ? pos.getZ() : pos.getX();
    }
}
