package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.frame.VaultPortalShape.View;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The §3.2 frame rules, on a world that holds only what each test put there and air. */
class VaultPortalShapeTest {
    private static final int BOTTOM = 64;

    /** Further from the origin than any test builds, and than a capped walk can read. */
    private static final int WORLD_EDGE = 128;

    /** A world of air with a ring of frames in it; the sizes are the opening's, as §3.2 gives them. */
    private static final class World implements View {
        private final Map<Cell, Kind> blocks = new HashMap<>();

        World put(Cell cell, Kind kind) {
            blocks.put(cell, kind);
            return this;
        }

        @Override
        public Kind kindAt(int x, int y, int z) {
            assertTrue(Math.abs(x) < WORLD_EDGE && Math.abs(y) < WORLD_EDGE && Math.abs(z) < WORLD_EDGE, "ran away");
            return blocks.getOrDefault(new Cell(x, y, z), Kind.AIR);
        }
    }

    /** One opening: its first column at along 1 and its bottom row at BOTTOM + 1, on the plane through 5. */
    private record Ring(Axis axis, int width, int height) {
        private Cell at(int along, int y) {
            return axis == Axis.X ? new Cell(along, y, 5) : new Cell(5, y, along);
        }

        private int right() {
            return width + 1;
        }

        private int top() {
            return BOTTOM + height + 1;
        }

        List<Cell> sides() {
            List<Cell> cells = new ArrayList<>();
            for (int along = 1; along < right(); along++) {
                cells.add(at(along, BOTTOM));
                cells.add(at(along, top()));
            }
            for (int y = BOTTOM + 1; y < top(); y++) {
                cells.add(at(0, y));
                cells.add(at(right(), y));
            }
            return cells;
        }

        List<Cell> corners() {
            return List.of(at(0, BOTTOM), at(right(), BOTTOM), at(0, top()), at(right(), top()));
        }

        List<Cell> opening() {
            List<Cell> cells = new ArrayList<>();
            for (int y = BOTTOM + 1; y < top(); y++) {
                for (int along = 1; along < right(); along++) {
                    cells.add(at(along, y));
                }
            }
            return cells;
        }

        Cell leftSide() {
            return at(0, BOTTOM + 1);
        }

        World built() {
            World world = new World();
            sides().forEach(cell -> world.put(cell, Kind.FRAME));
            return world;
        }

        VaultPortalShape expected() {
            Cell first = at(1, BOTTOM + 1);
            return new VaultPortalShape(axis, first.x(), first.y(), first.z(), width, height);
        }
    }

    private static Optional<VaultPortalShape> find(View view, Cell cell) {
        return VaultPortalShape.find(view, cell.x(), cell.y(), cell.z());
    }

    private static List<VaultPortalShape> findAll(View view, Cell cell) {
        return VaultPortalShape.findAll(view, cell.x(), cell.y(), cell.z());
    }

    private static void assertFinds(Ring ring, View view, Cell clicked) {
        assertEquals(Optional.of(ring.expected()), find(view, clicked), "clicked " + clicked);
    }

    private static void assertNothing(View view, Cell clicked) {
        assertEquals(List.of(), findAll(view, clicked), "clicked " + clicked);
        assertEquals(Optional.empty(), find(view, clicked), "clicked " + clicked);
    }

    @Test
    void theSmallestOpeningIsValid() {
        Ring ring = new Ring(Axis.X, 2, 3);
        assertFinds(ring, ring.built(), ring.leftSide());
    }

    @Test
    void theLargestOpeningIsValid() {
        Ring ring = new Ring(Axis.X, 21, 21);
        assertFinds(ring, ring.built(), ring.leftSide());
    }

    @Test
    void theLargestOpeningIsValidFromItsFarCorner() {
        Ring ring = new Ring(Axis.X, 21, 21);
        assertFinds(ring, ring.built().put(ring.at(ring.right(), ring.top()), Kind.FRAME), ring.at(ring.right(), ring.top()));
    }

    @Test
    void twentyTwoByTwentyTwoIsRejected() {
        Ring ring = new Ring(Axis.X, 22, 22);
        assertNothing(ring.built(), ring.leftSide());
    }

    @Test
    void twentyTwoWideIsRejected() {
        Ring ring = new Ring(Axis.X, 22, 21);
        assertNothing(ring.built(), ring.at(ring.right() / 2, BOTTOM));
    }

    @Test
    void twentyTwoTallIsRejected() {
        Ring ring = new Ring(Axis.X, 21, 22);
        assertNothing(ring.built(), ring.at(0, (BOTTOM + ring.top()) / 2));
    }

    @Test
    void oneWideIsRejected() {
        Ring ring = new Ring(Axis.X, 1, 3);
        assertNothing(ring.built(), ring.leftSide());
    }

    @Test
    void twoTallIsRejected() {
        Ring ring = new Ring(Axis.X, 2, 2);
        assertNothing(ring.built(), ring.leftSide());
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(value = Kind.class, names = "FRAME", mode = EnumSource.Mode.EXCLUDE)
    void anySideBlockNotAFrameFailsTheShape(Kind kind) {
        Ring ring = new Ring(Axis.X, 2, 3);
        for (Cell gap : ring.sides()) {
            World world = ring.built().put(gap, kind);
            for (Cell clicked : ring.sides()) {
                if (!clicked.equals(gap)) {
                    assertNothing(world, clicked);
                }
            }
        }
    }

    @Test
    void anObstructedCellFailsTheShape() {
        Ring ring = new Ring(Axis.X, 3, 4);
        for (Cell obstruction : ring.opening()) {
            World world = ring.built().put(obstruction, Kind.OTHER);
            for (Cell clicked : ring.sides()) {
                assertNothing(world, clicked);
            }
        }
    }

    @Test
    void aStrayFrameInTheOpeningFailsTheShape() {
        Ring ring = new Ring(Axis.X, 5, 5);
        assertNothing(ring.built().put(ring.at(3, BOTTOM + 3), Kind.FRAME), ring.leftSide());
    }

    @Test
    void anOpeningFullOfPortalIsAccepted() {
        Ring ring = new Ring(Axis.X, 2, 3);
        World world = ring.built();
        ring.opening().forEach(cell -> world.put(cell, Kind.PORTAL));
        assertFinds(ring, world, ring.leftSide());
    }

    @Test
    void anOpeningPartlyPortalIsAccepted() {
        Ring ring = new Ring(Axis.X, 2, 3);
        assertFinds(ring, ring.built().put(ring.at(2, BOTTOM + 2), Kind.PORTAL), ring.leftSide());
    }

    /** A corner placed on a lit ring finds the opening; the door caller's view must not, or it seals a live portal. */
    @Test
    void aViewAnsweringOtherForPortalKeepsALitRingFromTheDoorCaller() {
        Ring ring = new Ring(Axis.X, 2, 3);
        World world = ring.built();
        ring.opening().forEach(cell -> world.put(cell, Kind.PORTAL));
        Cell corner = ring.at(0, BOTTOM);
        world.put(corner, Kind.FRAME);
        View doorCaller = (x, y, z) -> world.kindAt(x, y, z) == Kind.PORTAL ? Kind.OTHER : world.kindAt(x, y, z);

        assertFinds(ring, world, corner);
        assertNothing(doorCaller, corner);
    }

    @Test
    void aFrameOnTheZAxisIsFoundOnThatAxis() {
        Ring ring = new Ring(Axis.Z, 2, 3);
        Optional<VaultPortalShape> found = find(ring.built(), ring.leftSide());
        assertEquals(Optional.of(Axis.Z), found.map(VaultPortalShape::axis));
        assertEquals(Optional.of(ring.expected()), found);
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(Axis.class)
    void everySideBlockFindsTheShape(Axis axis) {
        Ring ring = new Ring(axis, 3, 5);
        World world = ring.built();
        for (Cell clicked : ring.sides()) {
            assertFinds(ring, world, clicked);
        }
    }

    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(Axis.class)
    void everyCornerBlockFindsTheShape(Axis axis) {
        Ring ring = new Ring(axis, 2, 3);
        World world = ring.built();
        ring.corners().forEach(cell -> world.put(cell, Kind.FRAME));
        for (Cell clicked : ring.corners()) {
            assertFinds(ring, world, clicked);
        }
    }

    @Test
    void aClickThatIsNotAFrameFindsNothing() {
        Ring ring = new Ring(Axis.X, 2, 3);
        World world = ring.built();
        assertNothing(world, ring.at(1, BOTTOM + 1));
        assertNothing(world, ring.at(0, BOTTOM));
        assertNothing(world.put(ring.at(0, BOTTOM), Kind.OTHER), ring.at(0, BOTTOM));
        assertNothing(world.put(ring.at(1, BOTTOM + 1), Kind.PORTAL), ring.at(1, BOTTOM + 1));
    }

    @Test
    void aFrameBlockOutsideTheRingFindsNothing() {
        Ring ring = new Ring(Axis.X, 2, 3);
        Cell onTop = ring.at(1, ring.top() + 1);
        assertNothing(ring.built().put(onTop, Kind.FRAME), onTop);
    }

    @Test
    void aLoneFrameBlockFindsNothing() {
        assertNothing(new World().put(new Cell(0, 64, 0), Kind.FRAME), new Cell(0, 64, 0));
    }

    /** Every assignment of every kind to the four corners: §3.2 says they may hold anything. */
    @ParameterizedTest(name = "{displayName} [{0}]")
    @EnumSource(Axis.class)
    void cornersMayHoldAnything(Axis axis) {
        Ring ring = new Ring(axis, 2, 3);
        Kind[] kinds = Kind.values();
        List<Cell> corners = ring.corners();
        for (Kind first : kinds) {
            for (Kind second : kinds) {
                for (Kind third : kinds) {
                    for (Kind fourth : kinds) {
                        World world = ring.built().put(corners.get(0), first).put(corners.get(1), second)
                                .put(corners.get(2), third).put(corners.get(3), fourth);
                        assertFinds(ring, world, ring.leftSide());
                    }
                }
            }
        }
    }

    @Test
    void aBarRunningOnPastItsCornerStillMakesTheShape() {
        Ring ring = new Ring(Axis.X, 2, 3);
        World world = ring.built();
        for (int along = -40; along <= 40; along++) {
            world.put(ring.at(along, BOTTOM), Kind.FRAME);
        }
        assertFinds(ring, world, ring.leftSide());
    }

    @Test
    void anOpeningThatIsNotARectangleIsRejected() {
        Ring ring = new Ring(Axis.X, 3, 3);
        World world = ring.built();
        // The notch takes the opening's top right cell out into the top bar.
        world.put(ring.at(3, ring.top()), Kind.AIR).put(ring.at(3, ring.top() + 1), Kind.FRAME);
        assertNothing(world, ring.leftSide());
    }

    /** Air forever, so nothing but the step limit can stop the walk. */
    @Test
    void aRunThatNeverEndsFailsClosedWithinTheStepLimit() {
        Cell clicked = new Cell(0, 64, 0);
        int reach = VaultPortalShape.MAX_STEPS + 2;
        View endless = (x, y, z) -> {
            assertTrue(Math.abs(x) <= reach && Math.abs(y - 64) <= reach && Math.abs(z) <= reach, "read " + x + " " + y + " " + z);
            return x == 0 && y == 64 && z == 0 ? Kind.FRAME : Kind.AIR;
        };
        assertNothing(endless, clicked);
    }

    @Test
    void aRunOneStepOverTheLimitFailsClosed() {
        Ring tooWide = new Ring(Axis.X, VaultPortalShape.MAX_STEPS + 2, 3);
        World world = tooWide.built();
        // From the top corner the walk starts at one end of the run, so it alone sees the whole length.
        Cell corner = tooWide.at(0, tooWide.top());
        assertNothing(world.put(corner, Kind.FRAME), corner);
    }

    @Test
    void aFullLengthRunIsWithinTheLimit() {
        Ring widest = new Ring(Axis.X, VaultPortalShape.MAX_STEPS + 1, 3);
        World world = widest.built();
        Cell corner = widest.at(0, widest.top());
        assertFinds(widest, world.put(corner, Kind.FRAME), corner);
    }

    @Test
    void theLastBlockPlacedCompletesTheShape() {
        Ring ring = new Ring(Axis.X, 2, 3);
        for (Cell last : ring.sides()) {
            World world = ring.built().put(last, Kind.AIR);
            for (Cell other : ring.sides()) {
                if (!other.equals(last)) {
                    assertNothing(world, other);
                }
            }
            assertFinds(ring, world.put(last, Kind.FRAME), last);
        }
    }

    @Test
    void aBarSharedByTwoStackedOpeningsFindsBoth() {
        Ring lower = new Ring(Axis.X, 2, 3);
        World world = lower.built();
        int shared = lower.top();
        int upperTop = shared + 4;
        for (int along = 1; along <= 2; along++) {
            world.put(lower.at(along, upperTop), Kind.FRAME);
        }
        for (int y = shared + 1; y < upperTop; y++) {
            world.put(lower.at(0, y), Kind.FRAME).put(lower.at(3, y), Kind.FRAME);
        }
        Cell first = lower.at(1, shared + 1);
        VaultPortalShape upper = new VaultPortalShape(Axis.X, first.x(), first.y(), first.z(), 2, 3);

        Cell onTheBar = lower.at(1, shared);
        assertEquals(List.of(upper, lower.expected()), findAll(world, onTheBar));
        assertEquals(Optional.of(upper), find(world, onTheBar));
        assertEquals(List.of(upper), findAll(world, lower.at(1, upperTop)));
    }

    /** A middle block has two cells of the one opening at its diagonals, and is still one shape. */
    @Test
    void aBlockBesideTwoOfTheOpeningsCellsFindsItOnce() {
        Ring ring = new Ring(Axis.X, 3, 3);
        assertEquals(List.of(ring.expected()), findAll(ring.built(), ring.at(2, BOTTOM)));
    }

    @Test
    void aBlockInTwoFramesOnTwoAxesFindsBoth() {
        Ring alongX = new Ring(Axis.X, 2, 3);
        World world = alongX.built();
        // The Z frame stands on the same bottom block, at x 1, running through z 5.
        Cell shared = alongX.at(1, BOTTOM);
        Ring alongZ = new Ring(Axis.Z, 2, 3);
        for (Cell cell : alongZ.sides()) {
            world.put(new Cell(shared.x(), cell.y(), cell.z() + shared.z() - 1), Kind.FRAME);
        }
        Cell first = new Cell(shared.x(), BOTTOM + 1, shared.z());
        VaultPortalShape other = new VaultPortalShape(Axis.Z, first.x(), first.y(), first.z(), 2, 3);

        List<VaultPortalShape> found = findAll(world, shared);

        assertEquals(List.of(alongX.expected(), other), found);
        assertEquals(Optional.of(found.get(0)), find(world, shared));
    }

    @Test
    void cellsRunBottomRowFirst() {
        VaultPortalShape shape = new VaultPortalShape(Axis.X, 4, 70, 9, 2, 3);
        assertEquals(List.of(new Cell(4, 70, 9), new Cell(5, 70, 9), new Cell(4, 71, 9), new Cell(5, 71, 9),
                new Cell(4, 72, 9), new Cell(5, 72, 9)), shape.cells());
    }

    @Test
    void cellsOfAZShapeRunAlongZ() {
        VaultPortalShape shape = new VaultPortalShape(Axis.Z, 4, 70, 9, 2, 3);
        assertEquals(new Cell(4, 70, 9), shape.cells().get(0));
        assertEquals(new Cell(4, 70, 10), shape.cells().get(1));
        assertEquals(new Cell(4, 72, 10), shape.cells().get(5));
    }

    @Test
    void theRingsCellsAreTheOpeningTheShapeReports() {
        Ring ring = new Ring(Axis.Z, 3, 4);
        Optional<VaultPortalShape> found = find(ring.built(), ring.leftSide());
        assertTrue(found.isPresent());
        assertEquals(Set.copyOf(ring.opening()), Set.copyOf(found.get().cells()));
        assertFalse(found.get().cells().isEmpty());
    }
}
