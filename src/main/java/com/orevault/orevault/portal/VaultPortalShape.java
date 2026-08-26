package com.orevault.orevault.portal;

import com.orevault.orevault.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scans outward from a clicked Vault Frame block to find a valid rectangular portal frame.
 * Valid frame: all four sides Vault Frame; interior fully air or existing portal blocks;
 * interior min 2x3, max 21x21 (design spec section 3.2).
 */
public final class VaultPortalShape {
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    public record Result(boolean valid, BlockPos minCorner, int width, int height, Direction.Axis axis) {
        public static Result invalid() {
            return new Result(false, BlockPos.ZERO, 0, 0, Direction.Axis.X);
        }
    }

    private VaultPortalShape() {
    }

    public static Result scan(Level level, BlockPos clicked) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            Direction longDir = axis == Direction.Axis.X ? Direction.SOUTH : Direction.EAST;

            BlockPos left = walkToEnd(level, clicked, longDir.getOpposite());
            BlockPos right = walkToEnd(level, clicked, longDir);
            int width = dist(right, left, longDir) + 1;
            if (width < MIN_WIDTH || width > MAX_WIDTH) {
                continue;
            }

            // Pillar bottoms: walk down both end columns.
            BlockPos bottomLeft = left;
            BlockPos bottomRight = right;
            while (isFrame(level.getBlockState(bottomLeft.below()))) {
                bottomLeft = bottomLeft.below();
            }
            while (isFrame(level.getBlockState(bottomRight.below()))) {
                bottomRight = bottomRight.below();
            }
            if (bottomLeft.getY() != bottomRight.getY()) {
                continue;
            }
            // Bottom beam must span the whole width.
            if (!rowIsFrame(level, bottomLeft, bottomRight, longDir)) {
                continue;
            }

            // Pillar tops.
            BlockPos topLeft = bottomLeft;
            BlockPos topRight = bottomRight;
            while (isFrame(level.getBlockState(topLeft.above())) && isFrame(level.getBlockState(topRight.above()))) {
                topLeft = topLeft.above();
                topRight = topRight.above();
            }
            if (!rowIsFrame(level, topLeft, topRight, longDir)) {
                continue;
            }
            int height = topLeft.getY() - bottomLeft.getY() + 1;
            if (height < MIN_HEIGHT || height > MAX_HEIGHT) {
                continue;
            }

            if (validateInterior(level, bottomLeft, width, height, longDir)) {
                return new Result(true, bottomLeft, width, height, axis);
            }
        }
        return Result.invalid();
    }

    private static int dist(BlockPos a, BlockPos b, Direction dir) {
        return switch (dir.getAxis()) {
            case X -> Math.abs(a.getX() - b.getX());
            case Y -> Math.abs(a.getY() - b.getY());
            case Z -> Math.abs(a.getZ() - b.getZ());
        };
    }

    private static BlockPos walkToEnd(Level level, BlockPos start, Direction dir) {
        BlockPos pos = start;
        while (isFrame(level.getBlockState(pos.relative(dir)))) {
            pos = pos.relative(dir);
        }
        return pos;
    }

    private static boolean rowIsFrame(Level level, BlockPos from, BlockPos to, Direction longDir) {
        BlockPos cursor = from;
        while (!cursor.equals(to.relative(longDir))) {
            if (!isFrame(level.getBlockState(cursor))) {
                return false;
            }
            cursor = cursor.relative(longDir);
        }
        return true;
    }

    private static boolean validateInterior(Level level, BlockPos bottomLeft, int width, int height, Direction longDir) {
        for (int w = 1; w < width - 1; w++) {
            for (int h = 0; h < height; h++) {
                BlockPos pos = bottomLeft.relative(longDir, w).above(h);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !state.is(ModBlocks.VAULT_PORTAL)) {
                    return false;
                }
            }
        }
        // Perimeter must all be frame.
        for (int w = 0; w < width; w++) {
            BlockPos bottom = bottomLeft.relative(longDir, w);
            BlockPos top = bottom.above(height - 1);
            if (!isFrame(level.getBlockState(bottom)) || !isFrame(level.getBlockState(top))) {
                return false;
            }
        }
        for (int h = 0; h < height; h++) {
            BlockPos left = bottomLeft.above(h);
            BlockPos right = left.relative(longDir, width - 1);
            if (!isFrame(level.getBlockState(left)) || !isFrame(level.getBlockState(right))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFrame(BlockState state) {
        return state.is(ModBlocks.VAULT_FRAME);
    }
}
