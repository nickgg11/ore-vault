package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** §3.2: the return position lies outside every portal plane, on the side the player approached from. */
class PortalExitTest {
    @Test
    void walkingInAlongThePositiveNormalComesOutOnTheNegativeSide() {
        assertEquals(-1, PortalExit.side(0.2, 0));
    }

    @Test
    void walkingInAlongTheNegativeNormalComesOutOnThePositiveSide() {
        assertEquals(1, PortalExit.side(-0.2, 0));
    }

    @Test
    void standingStillTheFacingDecides() {
        assertEquals(-1, PortalExit.side(0.0, 1));
        assertEquals(1, PortalExit.side(0.0, -1));
    }

    @Test
    void creepingBelowTheThresholdCountsAsStill() {
        assertEquals(1, PortalExit.side(0.01, -1));
        assertEquals(-1, PortalExit.side(-0.009, 1));
    }

    @Test
    void movementWinsOverFacing() {
        assertEquals(-1, PortalExit.side(0.5, -1));
    }

    @Test
    void facingAlongThePlaneComesOutOnThePositiveSide() {
        assertEquals(1, PortalExit.side(0.0, 0));
    }

    @Test
    void oneCellOfPortalIsLeftByOneStep() {
        assertEquals(1, PortalExit.stepsOut(step -> false));
    }

    @Test
    void twoRingsFaceToFaceAreBothCrossed() {
        assertEquals(2, PortalExit.stepsOut(step -> step == 1));
    }

    @Test
    void theWalkStopsAfterTheLimit() {
        assertEquals(PortalExit.MAX_PORTAL_CELLS + 1, PortalExit.stepsOut(step -> true));
    }

    @Test
    void theFirstClearCellEndsTheWalk() {
        assertEquals(3, PortalExit.stepsOut(step -> step < 3 || step == 4));
    }
}
