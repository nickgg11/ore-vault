package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/** §3.2 safe arrival (old #77): feet in a two-high pocket of air with something under it, searched down from the surface. */
class SafeArrivalTest {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 319;
    private static final int GRASS = 250;

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
        // Built over the anchor: the heightmap is above the roof at 260, and the room under it is the first pocket that fits.
        SafeArrival.Column roofed = column(GRASS, 260, 261, 262, 263, 264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274,
                275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289, 290, 291, 292, 293, 294, 295, 296, 297,
                298, 299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319);
        assertEquals(GRASS + 1, SafeArrival.feetY(MAX_Y + 1, MIN_Y, MAX_Y, roofed));
    }

    @Test
    void aOneHighGapIsNotAPocket() {
        // Air at 251 only, stone from 252 to 255, air above: the player stands on the stone.
        assertEquals(256, SafeArrival.feetY(256, MIN_Y, MAX_Y, column(GRASS, 252, 253, 254, 255)));
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
        // Grass at 250, a torch on it at 251 (no collision, not motion-blocking, so the heightmap is 251), and a tunnel dug
        // at 240 to 241 under the grass: the arrival is on the grass, by the torch, not down in the tunnel.
        SafeArrival.Column torch = y -> y == 251 || y > 251 || y == 240 || y == 241 ? SafeArrival.Space.OPEN
                : SafeArrival.Space.SOLID;
        assertEquals(251, SafeArrival.feetY(251, MIN_Y, MAX_Y, torch));
    }

    @Test
    void lavaIsNoFloor() {
        // A lava pool on the grass at 251, open above it: the search goes past the lava to the pocket under the pool's
        // stone bed at 245, never stands on the lava.
        SafeArrival.Column pool = y -> {
            if (y == 251) {
                return SafeArrival.Space.FLUID;
            }
            return y > 251 || y == 246 || y == 247 ? SafeArrival.Space.OPEN : SafeArrival.Space.SOLID;
        };
        assertEquals(246, SafeArrival.feetY(252, MIN_Y, MAX_Y, pool));
    }

    @Test
    void feetAndHeadInWaterAreNoPocket() {
        SafeArrival.Column flooded = y -> y > 250 && y < 260 ? SafeArrival.Space.FLUID
                : y >= 260 ? SafeArrival.Space.OPEN : SafeArrival.Space.SOLID;
        // Open from 260 up, but the floor at 259 is water: no pocket in the column, so the default.
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
}
