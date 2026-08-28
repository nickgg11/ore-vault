package com.orevault.orevault.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Geometry tests for {@link VaultPortalShape} (§3.2): valid frames are found
 * and invalid ones rejected, without needing a running level.
 */
class VaultPortalShapeTest {

    /** In-memory view: unspecified positions default to AIR. */
    private static final class TestView implements VaultPortalShape.View {
        private final Map<BlockPos, VaultPortalShape.Kind> kinds = new HashMap<>();

        TestView put(BlockPos pos, VaultPortalShape.Kind kind) {
            kinds.put(pos, kind);
            return this;
        }

        /** Builds a complete rectangular frame on the given plane (all four sides). */
        TestView frame(int minH, int minY, int fixed, int frameWidth, int frameHeight, Direction.Axis axis) {
            int maxH = minH + frameWidth - 1;
            int maxY = minY + frameHeight - 1;
            for (int h = minH; h <= maxH; h++) {
                put(at(h, minY, fixed, axis), VaultPortalShape.Kind.FRAME);
                put(at(h, maxY, fixed, axis), VaultPortalShape.Kind.FRAME);
            }
            for (int y = minY; y <= maxY; y++) {
                put(at(minH, y, fixed, axis), VaultPortalShape.Kind.FRAME);
                put(at(maxH, y, fixed, axis), VaultPortalShape.Kind.FRAME);
            }
            return this;
        }

        @Override
        public VaultPortalShape.Kind kindAt(int x, int y, int z) {
            return kinds.getOrDefault(new BlockPos(x, y, z), VaultPortalShape.Kind.AIR);
        }
    }

    private static BlockPos at(int h, int y, int fixed, Direction.Axis axis) {
        return axis == Direction.Axis.X ? new BlockPos(h, y, fixed) : new BlockPos(fixed, y, h);
    }

    @Test
    void validTwoByThreeFrameIsFound() {
        // Interior 2 wide x 3 tall -> frame 4 x 5, plane z = 0, y 100..104.
        TestView view = new TestView().frame(0, 100, 0, 4, 5, Direction.Axis.X);

        // Click on the top row (a frame block) exercises the edge-column vertical scan.
        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(1, 100, 0));

        assertTrue(found.isPresent());
        VaultPortalShape shape = found.get();
        assertEquals(4, shape.frameWidth());
        assertEquals(5, shape.frameHeight());
        assertEquals(Direction.Axis.X, shape.axis());
        assertEquals(new BlockPos(0, 100, 0), shape.minCorner());
        assertEquals(2 * 3, shape.interiorPositions().size());
    }

    @Test
    void frameWithTwentyTwoInteriorIsRejected() {
        // Interior 22 x 22 -> frame 24 x 24 exceeds the 21 x 21 limit.
        TestView view = new TestView().frame(0, 0, 0, 24, 24, Direction.Axis.X);

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(11, 0, 0));

        assertTrue(found.isEmpty());
    }

    @Test
    void incompleteSideIsRejected() {
        TestView view = new TestView().frame(0, 100, 0, 4, 5, Direction.Axis.X);
        // Punch a hole in the top row.
        view.put(new BlockPos(1, 104, 0), VaultPortalShape.Kind.AIR);

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(0, 102, 0));

        assertTrue(found.isEmpty());
    }

    @Test
    void interiorObstructionIsRejected() {
        TestView view = new TestView().frame(0, 100, 0, 4, 5, Direction.Axis.X);
        // A non-air, non-portal block inside the interior invalidates the frame.
        view.put(new BlockPos(1, 101, 0), VaultPortalShape.Kind.OTHER);

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(0, 102, 0));

        assertTrue(found.isEmpty());
    }

    @Test
    void existingPortalInteriorIsAccepted() {
        TestView view = new TestView().frame(0, 100, 0, 4, 5, Direction.Axis.X);
        view.put(new BlockPos(1, 101, 0), VaultPortalShape.Kind.PORTAL);

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(0, 102, 0));

        assertTrue(found.isPresent());
    }

    @Test
    void zAxisFrameIsFound() {
        // Same geometry on the Z axis: plane x = 0.
        TestView view = new TestView().frame(0, 100, 0, 4, 5, Direction.Axis.Z);

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(0, 102, 0));

        assertTrue(found.isPresent());
        assertEquals(Direction.Axis.Z, found.get().axis());
        assertEquals(new BlockPos(0, 100, 0), found.get().minCorner());
    }

    @Test
    void nonFrameClickIsRejected() {
        TestView view = new TestView(); // everything is air

        Optional<VaultPortalShape> found = VaultPortalShape.find(view, new BlockPos(0, 64, 0));

        assertTrue(found.isEmpty());
    }
}
