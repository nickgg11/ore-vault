package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.frame.ReturnPortal.Plan;
import com.orevault.orevault.core.frame.VaultPortalShape.Cell;
import com.orevault.orevault.core.frame.VaultPortalShape.Kind;
import com.orevault.orevault.core.signet.SignetTier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * §3.2: one 4×5 return portal at the team anchor, built on first entry and idempotent after, its tier read back from its
 * cells and only ever raised (old #98, #109; ADR 0010: cells before frames, so no door seals first).
 */
class ReturnPortalTest {
    private static final int MIN_Y = 0;
    private static final int GRASS = 250;
    private static final int BASE = GRASS + 1;
    private static final int TOP = 320;

    /** Ground to {@link #GRASS} in every column, air above, and whatever a test puts there. */
    private static final class World implements ReturnPortal.Blocks {
        private final Map<Cell, Kind> kinds = new HashMap<>();
        private final Map<Cell, Integer> ranks = new HashMap<>();
        private int ground = GRASS;

        World ground(int y) {
            ground = y;
            return this;
        }

        World put(Cell cell, Kind kind) {
            kinds.put(cell, kind);
            return this;
        }

        World portal(Cell cell, int rank) {
            kinds.put(cell, Kind.PORTAL);
            ranks.put(cell, rank);
            return this;
        }

        /** Writes a plan as the adapter does: cells as portal of the plan's rank, frames as frame. */
        World apply(Plan plan) {
            plan.cells().forEach(cell -> portal(cell, plan.rank()));
            plan.frames().forEach(cell -> put(cell, Kind.FRAME));
            return this;
        }

        World remove(Cell cell) {
            kinds.put(cell, Kind.AIR);
            ranks.remove(cell);
            return this;
        }

        @Override
        public Kind kindAt(int x, int y, int z) {
            assertTrue(y >= MIN_Y && y < TOP, "read outside the world at " + y);
            return kinds.getOrDefault(new Cell(x, y, z), y <= ground ? Kind.OTHER : Kind.AIR);
        }

        @Override
        public int rankAt(int x, int y, int z) {
            return ranks.getOrDefault(new Cell(x, y, z), 0);
        }
    }

    /** The adapter's form: the tier answers for itself. */
    private static ReturnPortal.Entering entering(SignetTier tier) {
        return new ReturnPortal.Entering() {
            @Override
            public int rank() {
                return tier.rank();
            }

            @Override
            public boolean mayPress(int portalRank) {
                return tier.mayPress(portalRank);
            }
        };
    }

    private static Plan build(World world, SignetTier tier) {
        return ReturnPortal.plan(world, ReturnPortal.baseY(world, TOP - 1, MIN_Y), entering(tier));
    }

    private static Set<Cell> places(int baseY, ReturnPortal.Role role) {
        Set<Cell> cells = new HashSet<>();
        ReturnPortal.layout(baseY).stream().filter(place -> place.role() == role).forEach(place -> cells.add(place.cell()));
        return cells;
    }

    @Test
    void theLayoutIsA4By5RingWithA2By3OpeningOnThePlaneSouthOfTheAnchor() {
        List<ReturnPortal.Place> layout = ReturnPortal.layout(BASE);
        assertEquals(20, layout.size());
        assertEquals(Set.of(new Cell(0, BASE + 1, 3), new Cell(1, BASE + 1, 3), new Cell(0, BASE + 2, 3), new Cell(1, BASE + 2, 3),
                new Cell(0, BASE + 3, 3), new Cell(1, BASE + 3, 3)), places(BASE, ReturnPortal.Role.CELL));
        assertEquals(Set.of(new Cell(-1, BASE, 3), new Cell(2, BASE, 3), new Cell(-1, BASE + 4, 3), new Cell(2, BASE + 4, 3)),
                places(BASE, ReturnPortal.Role.CORNER));
        assertEquals(10, places(BASE, ReturnPortal.Role.SIDE).size());
        layout.forEach(place -> assertEquals(ReturnPortal.PLANE_Z, place.cell().z()));
    }

    @Test
    void theRingIsAValidOpeningToTheScanner() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        VaultPortalShape shape = VaultPortalShape.around((x, y, z) -> world.kindAt(x, y, z), VaultPortalShape.Axis.X, 0, BASE + 1,
                ReturnPortal.PLANE_Z).orElseThrow();
        assertEquals(new VaultPortalShape(VaultPortalShape.Axis.X, 0, BASE + 1, ReturnPortal.PLANE_Z, 2, 3), shape);
    }

    @Test
    void theFirstEntryBuildsOnTheGrassCellsFirst() {
        World world = new World();
        Plan plan = build(world, SignetTier.WORN);
        assertFalse(plan.blocked());
        assertEquals(BASE, plan.baseY());
        assertEquals(SignetTier.WORN.rank(), plan.rank());
        assertEquals(places(BASE, ReturnPortal.Role.CELL), Set.copyOf(plan.cells()));
        Set<Cell> frames = places(BASE, ReturnPortal.Role.SIDE);
        frames.addAll(places(BASE, ReturnPortal.Role.CORNER));
        assertEquals(frames, Set.copyOf(plan.frames()));
    }

    @Test
    void aStandingPortalOfTheSameTierNeedsNothing() {
        World world = new World();
        world.apply(build(world, SignetTier.HEIRLOOM));
        Plan again = build(world, SignetTier.HEIRLOOM);
        assertTrue(again.changesNothing());
        assertEquals(SignetTier.HEIRLOOM.rank(), again.rank());
    }

    @Test
    void aLowerTierNeverDowngradesIt() {
        World world = new World();
        world.apply(build(world, SignetTier.FABLED));
        Plan worn = build(world, SignetTier.WORN);
        assertTrue(worn.changesNothing());
        assertEquals(SignetTier.FABLED.rank(), worn.rank());
    }

    @Test
    void aHigherTierUpgradesEveryCellAndNoFrame() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        Plan primordial = build(world, SignetTier.PRIMORDIAL);
        assertEquals(SignetTier.PRIMORDIAL.rank(), primordial.rank());
        assertEquals(places(BASE, ReturnPortal.Role.CELL), Set.copyOf(primordial.cells()));
        assertEquals(List.of(), primordial.frames());
    }

    @Test
    void theTierIsTheHighestCellsSoAMixedPortalIsRaisedToIt() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        world.portal(new Cell(1, BASE + 2, 3), SignetTier.FABLED.rank());
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(SignetTier.FABLED.rank(), plan.rank());
        assertEquals(5, plan.cells().size());
    }

    @Test
    void aSealedDoorInTheRingTakesThePortal() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        places(BASE, ReturnPortal.Role.CELL).forEach(cell -> world.portal(cell, 0));
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(BASE, plan.baseY());
        assertEquals(SignetTier.WORN.rank(), plan.rank());
        assertEquals(6, plan.cells().size());
        assertEquals(List.of(), plan.frames());
    }

    @Test
    void aDissolvedPortalIsRebuiltOnItsOwnFramesNotOnTopOfThem() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        places(BASE, ReturnPortal.Role.CELL).forEach(world::remove);
        Cell broken = new Cell(-1, BASE + 2, 3);
        world.remove(broken);
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(BASE, plan.baseY());
        assertEquals(6, plan.cells().size());
        assertEquals(List.of(broken), plan.frames());
    }

    @Test
    void aBrokenCornerIsPutBack() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        Cell corner = new Cell(2, BASE + 4, 3);
        world.remove(corner);
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(List.of(), plan.cells());
        assertEquals(List.of(corner), plan.frames());
    }

    @Test
    void aRoofBuiltOverTheStandingPortalDoesNotMoveIt() {
        World world = new World();
        world.apply(build(world, SignetTier.WORN));
        for (int x = -1; x <= 2; x++) {
            world.put(new Cell(x, BASE + 8, 3), Kind.OTHER);
        }
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(BASE, plan.baseY());
        assertTrue(plan.changesNothing());
    }

    @Test
    void theGroundIsTheHighestOfTheFourColumns() {
        World world = new World();
        world.put(new Cell(2, GRASS + 1, 3), Kind.OTHER).put(new Cell(2, GRASS + 2, 3), Kind.OTHER);
        Plan plan = build(world, SignetTier.WORN);
        assertEquals(GRASS + 3, plan.baseY());
    }

    @Test
    void somethingOnASideBlocksTheWholeBuild() {
        World world = new World();
        world.put(new Cell(-1, BASE + 2, 3), Kind.OTHER);
        Plan plan = ReturnPortal.plan(world, BASE, entering(SignetTier.WORN));
        assertTrue(plan.blocked());
        assertEquals(List.of(), plan.cells());
        assertEquals(List.of(), plan.frames());
    }

    @Test
    void somethingInTheOpeningBlocksTheWholeBuild() {
        World world = new World();
        world.put(new Cell(1, BASE + 3, 3), Kind.OTHER);
        Plan plan = ReturnPortal.plan(world, BASE, entering(SignetTier.WORN));
        assertTrue(plan.blocked());
        assertFalse(plan.changesNothing());
    }

    @Test
    void somethingOnACornerIsLeftAndTheRestBuilt() {
        World world = new World();
        Cell corner = new Cell(-1, BASE + 4, 3);
        world.put(corner, Kind.OTHER);
        Plan plan = ReturnPortal.plan(world, BASE, entering(SignetTier.WORN));
        assertFalse(plan.blocked());
        assertEquals(6, plan.cells().size());
        assertEquals(13, plan.frames().size());
        assertFalse(plan.frames().contains(corner));
    }
}
