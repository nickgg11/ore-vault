package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orevault.orevault.core.gen.LayerStack;
import com.orevault.orevault.core.gen.VaultVariant;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** §3.2 safe arrival (old #77): feet in a two-high pocket of air with something under it, searched down from the surface. */
class SafeArrivalTest {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 319;
    private static final int GRASS = 100;

    /** A column solid from the bottom to {@code top} inclusive, air above, with any extra solid blocks named. */
    private static SafeArrival.Column column(int top, int... alsoSolid) {
        Set<Integer> solid = new HashSet<>();
        for (int y : alsoSolid) {
            solid.add(y);
        }
        return air(y -> y > top && !solid.contains(y));
    }

    /** Open where the predicate holds, solid elsewhere. */
    private static SafeArrival.Column air(IntPredicate open) {
        return y -> open.test(y) ? SafeArrival.Space.OPEN : SafeArrival.Space.SOLID;
    }

    @Test
    void theGeneratedSurfaceStandsOnTheGrass() {
        assertEquals(GRASS + 1, SafeArrival.feetY(GRASS + 1, MIN_Y, MAX_Y, column(GRASS)));
    }

    @Test
    void aPillarOverTheAnchorIsStoodOn() {
        assertEquals(GRASS + 4, SafeArrival.feetY(GRASS + 4, MIN_Y, MAX_Y, column(GRASS + 3)));
    }

    @Test
    void aRoofLeavesThePocketUnderIt() {
        // Built over the anchor: the heightmap is above the roof at 110, and the room under it is the first pocket that fits.
        SafeArrival.Column roofed = column(GRASS, IntStream.rangeClosed(110, MAX_Y).toArray());
        assertEquals(GRASS + 1, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, roofed));
    }

    @Test
    void aOneHighGapIsNotAPocket() {
        // Air at 101 only, stone from 102 to 105, air above: the player stands on the stone.
        assertEquals(106, SafeArrival.feetY(106, MIN_Y, MAX_Y, column(GRASS, 102, 103, 104, 105)));
    }

    @Test
    void aPocketWithNothingUnderItIsNotStoodIn() {
        // A surface given too high, over open air: the search goes on down to the ground rather than land the player in mid-air.
        assertEquals(GRASS + 1, SafeArrival.feetY(300, MIN_Y, MAX_Y, column(GRASS)));
        // A shaft open to the sky: the bottom of it is where the player lands.
        SafeArrival.Column open = air(y -> y >= 200);
        assertEquals(200, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, open));
    }

    @Test
    void aOneHighGapUnderARoofIsNoPocketAtAll() {
        // Air at 200 only, solid above it to the top: the feet would fit, the head would not.
        SafeArrival.Column gap = air(y -> y == 200);
        assertEquals(SafeArrival.DEFAULT_FEET_Y, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, gap));
    }

    @Test
    void aTorchAtTheFeetIsStoodBesideNotFallenThrough() {
        // Grass at 100, a torch on it at 101 (no collision, not motion-blocking, so the heightmap is 101), and a tunnel dug
        // at 90 to 91 under the grass: the arrival is on the grass, by the torch, not down in the tunnel.
        SafeArrival.Column torch = y -> y == 101 || y > 101 || y == 90 || y == 91 ? SafeArrival.Space.OPEN
                : SafeArrival.Space.SOLID;
        assertEquals(101, SafeArrival.feetY(101, MIN_Y, MAX_Y, torch));
    }

    @Test
    void lavaIsNoFloor() {
        // A lava pool on the grass at 101, open above it: the search goes past the lava to the pocket under the pool's
        // stone bed at 95, never stands on the lava.
        SafeArrival.Column pool = y -> {
            if (y == 101) {
                return SafeArrival.Space.FLUID;
            }
            return y > 101 || y == 96 || y == 97 ? SafeArrival.Space.OPEN : SafeArrival.Space.SOLID;
        };
        assertEquals(96, SafeArrival.feetY(102, MIN_Y, MAX_Y, pool));
    }

    @Test
    void feetAndHeadInWaterAreNoPocket() {
        SafeArrival.Column flooded = y -> y > 100 && y < 110 ? SafeArrival.Space.FLUID
                : y >= 110 ? SafeArrival.Space.OPEN : SafeArrival.Space.SOLID;
        // Open from 110 up, but the floor at 109 is water: no pocket in the column, so the default.
        assertEquals(SafeArrival.DEFAULT_FEET_Y, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, flooded));
    }

    @Test
    void aColumnFilledToTheTopFallsBackToTheDefault() {
        assertEquals(SafeArrival.DEFAULT_FEET_Y, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, column(MAX_Y)));
    }

    @Test
    void theHeadroomNeverReadsAboveTheWorld() {
        // Solid to 317: 318 and 319 are the last two blocks of air, and nothing above 319 is asked.
        SafeArrival.Column capped = air(y -> {
            if (y > MAX_Y) {
                throw new AssertionError("read above the world: " + y);
            }
            return y > 317;
        });
        assertEquals(318, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, capped));
    }

    @Test
    void theFloorIsNeverReadBelowTheWorld() {
        SafeArrival.Column empty = air(y -> {
            if (y < MIN_Y) {
                throw new AssertionError("read below the world: " + y);
            }
            return true;
        });
        assertEquals(SafeArrival.DEFAULT_FEET_Y, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, empty));
    }

    @Test
    void theExpandedVaultHasTheSameSurface() {
        assertEquals(GRASS + 1, SafeArrival.feetY(GRASS + 1, -64, MAX_Y, column(GRASS)));
    }

    /**
     * The default is the first air above the grass of the built-in base stack (§3.1; ADR 0009, amended 2026-11-21), so it
     * cannot drift from the stack when the surface moves again.
     */
    @Test
    void theDefaultIsTheFirstAirAboveTheBuiltInGrass() {
        VaultVariant base = VaultVariant.BASE;
        java.util.List<String> column = LayerStack.builtIn(base).column();
        int grass = column.lastIndexOf("minecraft:grass_block") + base.minY();
        assertEquals(GRASS, grass);
        assertEquals(LayerStack.AIR, column.get(grass + 1 - base.minY()));
        assertEquals(grass + 1, SafeArrival.DEFAULT_FEET_Y);
    }
}
