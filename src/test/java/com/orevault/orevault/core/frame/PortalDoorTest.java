package com.orevault.orevault.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orevault.orevault.core.frame.PortalDoor.Leaf;
import com.orevault.orevault.core.frame.PortalDoor.LeafFrame;
import com.orevault.orevault.core.frame.PortalDoor.Segment;
import com.orevault.orevault.core.frame.PortalDoor.Swing;
import com.orevault.orevault.core.frame.PortalDoor.Tile;
import com.orevault.orevault.core.frame.VaultPortalShape.Axis;
import java.util.List;
import org.junit.jupiter.api.Test;

/** §3.2 the swing on approach (E3-S13, #401; Nick, 2026-11-08, on the rendered preview). */
class PortalDoorTest {
    private static final double EPS = 1e-9;

    /** A 5 wide, 7 tall opening on axis X from (10, 64, 20). */
    private static final VaultPortalShape FIVE_BY_SEVEN = new VaultPortalShape(Axis.X, 10, 64, 20, 5, 7);

    @Test
    void leavesAreThreePixelsLikeAVanillaDoor() {
        assertEquals(3, PortalDoor.LEAF_THICKNESS_PX);
        assertEquals(3 / 16.0, PortalDoor.leafThickness(), EPS);
    }

    @Test
    void openDistanceIsSixBlocksUntilHalfTheWidthPlusTwoIsMore() {
        assertEquals(6.0, PortalDoor.openDistance(2), EPS);
        assertEquals(6.0, PortalDoor.openDistance(8), EPS);
        assertEquals(6.5, PortalDoor.openDistance(9), EPS);
        assertEquals(12.5, PortalDoor.openDistance(21), EPS);
    }

    @Test
    void closesTwoBlocksFurtherOut() {
        assertEquals(8.0, PortalDoor.closeDistance(2), EPS);
        assertEquals(14.5, PortalDoor.closeDistance(21), EPS);
    }

    @Test
    void wantsOpenHasTheTwoBlockGap() {
        assertTrue(PortalDoor.wantsOpen(false, 6.0, 5));
        assertFalse(PortalDoor.wantsOpen(false, 6.01, 5));
        assertTrue(PortalDoor.wantsOpen(true, 7.9, 5));
        assertTrue(PortalDoor.wantsOpen(true, 8.0, 5));
        assertFalse(PortalDoor.wantsOpen(true, 8.01, 5));
    }

    @Test
    void distanceIsToTheNearestPointOfTheOpening() {
        // the plane is at z 20.5, the opening spans x 10..15 and y 64..71
        assertEquals(0.0, PortalDoor.distance(FIVE_BY_SEVEN, 12, 65, 20.5), EPS);
        assertEquals(4.5, PortalDoor.distance(FIVE_BY_SEVEN, 12, 65, 25), EPS);
        assertEquals(4.5, PortalDoor.distance(FIVE_BY_SEVEN, 12, 65, 16), EPS);
        assertEquals(5.0, PortalDoor.distance(FIVE_BY_SEVEN, 5, 65, 20.5), EPS);
        assertEquals(5.0, PortalDoor.distance(FIVE_BY_SEVEN, 15 + 3, 71 + 4, 20.5), EPS);
    }

    @Test
    void distanceOnAxisZMeasuresAlongZ() {
        VaultPortalShape onZ = new VaultPortalShape(Axis.Z, 20, 64, 10, 5, 7);
        assertEquals(4.5, PortalDoor.distance(onZ, 25, 65, 12), EPS);
        assertEquals(5.0, PortalDoor.distance(onZ, 20.5, 65, 5), EPS);
    }

    @Test
    void sideIsWhichWayAcrossThePlaneThePlayerStands() {
        assertEquals(1, PortalDoor.side(FIVE_BY_SEVEN, 12, 23));
        assertEquals(-1, PortalDoor.side(FIVE_BY_SEVEN, 12, 18));
        VaultPortalShape onZ = new VaultPortalShape(Axis.Z, 20, 64, 10, 5, 7);
        assertEquals(1, PortalDoor.side(onZ, 23, 12));
        assertEquals(-1, PortalDoor.side(onZ, 18, 12));
    }

    /** The door model's front faces south on axis X and, turned by y 90, west on axis Z. */
    @Test
    void frontSideFollowsTheDoorModel() {
        assertEquals(1, PortalDoor.frontSide(Axis.X));
        assertEquals(-1, PortalDoor.frontSide(Axis.Z));
    }

    @Test
    void easeOutStartsFastAndEndsSlow() {
        assertEquals(0.0, PortalDoor.easeOut(0), EPS);
        assertEquals(1.0, PortalDoor.easeOut(1), EPS);
        assertTrue(PortalDoor.easeOut(0.5) > 0.5);
        assertTrue(PortalDoor.easeOut(0.1) - PortalDoor.easeOut(0) > PortalDoor.easeOut(1) - PortalDoor.easeOut(0.9));
        assertEquals(0.0, PortalDoor.easeOut(-1), EPS);
        assertEquals(1.0, PortalDoor.easeOut(2), EPS);
    }

    @Test
    void aFullSwingTakesTenTicks() {
        Swing opening = Swing.closed().toward(90, 100);
        assertEquals(0.0, opening.angleAt(100), EPS);
        assertTrue(opening.angleAt(105) > 45);
        assertTrue(opening.angleAt(109.9) < 90);
        assertEquals(90.0, opening.angleAt(110), EPS);
        assertEquals(90.0, opening.angleAt(500), EPS);
    }

    @Test
    void closingSwingsBackToZeroInTenTicks() {
        Swing open = Swing.closed().toward(90, 0);
        Swing closing = open.toward(0, 50);
        assertEquals(90.0, closing.angleAt(50), EPS);
        assertEquals(0.0, closing.angleAt(60), EPS);
    }

    @Test
    void turningBackMidSwingStartsFromTheCurrentAngleWithoutAJump() {
        Swing opening = Swing.closed().toward(90, 0);
        double at = opening.angleAt(4);
        Swing back = opening.toward(0, 4);
        assertEquals(at, back.angleAt(4), EPS);
        // the shorter arc takes proportionally fewer ticks
        assertEquals(0.0, back.angleAt(4 + 10 * at / 90), 1e-6);
    }

    @Test
    void towardTheSameTargetKeepsTheSwing() {
        Swing opening = Swing.closed().toward(90, 0);
        assertEquals(opening, opening.toward(90, 5));
    }

    @Test
    void closedIsAtRest() {
        assertTrue(Swing.closed().isClosedAt(0));
        assertFalse(Swing.closed().toward(90, 0).isClosedAt(1));
        assertTrue(Swing.closed().toward(90, 0).toward(0, 20).isClosedAt(30));
    }

    @Test
    void leavesHingeOnTheJambsOnThePlayersFace() {
        LeafFrame left = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.LEFT, 1, 0);
        assertEquals(10.0, left.hingeAlong(), EPS);
        assertEquals(20.5 + 1.5 / 16, left.hingeAcross(), EPS);
        LeafFrame right = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.RIGHT, -1, 0);
        assertEquals(15.0, right.hingeAlong(), EPS);
        assertEquals(20.5 - 1.5 / 16, right.hingeAcross(), EPS);
    }

    @Test
    void closedLeavesMeetAtTheMiddleAndFillTheThickness() {
        LeafFrame left = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.LEFT, 1, 0);
        double[] freeEnd = left.point(PortalDoor.leafLength(5), 0);
        assertEquals(12.5, freeEnd[0], EPS);
        double[] backFace = left.point(0, PortalDoor.leafThickness());
        assertEquals(20.5 - 1.5 / 16, backFace[1], EPS);
        LeafFrame right = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.RIGHT, 1, 0);
        assertEquals(12.5, right.point(PortalDoor.leafLength(5), 0)[0], EPS);
    }

    @Test
    void openLeavesStandTowardThePlayerInsideTheOpening() {
        LeafFrame left = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.LEFT, 1, 90);
        double[] freeEnd = left.point(2.5, 0);
        assertEquals(10.0, freeEnd[0], EPS);
        assertEquals(20.5 + 1.5 / 16 + 2.5, freeEnd[1], EPS);
        // the thickness runs into the opening, not into the jamb
        assertEquals(10.0 + 3 / 16.0, left.point(0, PortalDoor.leafThickness())[0], EPS);

        LeafFrame right = PortalDoor.leaf(FIVE_BY_SEVEN, Leaf.RIGHT, -1, 90);
        double[] rightEnd = right.point(2.5, 0);
        assertEquals(15.0, rightEnd[0], EPS);
        assertEquals(20.5 - 1.5 / 16 - 2.5, rightEnd[1], EPS);
        assertEquals(15.0 - 3 / 16.0, right.point(0, PortalDoor.leafThickness())[0], EPS);
    }

    @Test
    void anOddWidthSplitsTheCentreColumnBetweenTheLeaves() {
        assertEquals(List.of(new Segment(0, 1, 0), new Segment(1, 2, 1), new Segment(2, 2.5, 2)),
                PortalDoor.segments(Leaf.LEFT, 5));
        assertEquals(List.of(new Segment(2.5, 3, 2), new Segment(3, 4, 3), new Segment(4, 5, 4)),
                PortalDoor.segments(Leaf.RIGHT, 5));
        assertEquals(List.of(new Segment(0, 1, 0)), PortalDoor.segments(Leaf.LEFT, 2));
    }

    @Test
    void theRightLeafIsMeasuredFromTheHighJamb() {
        assertEquals(1.5, PortalDoor.fromHinge(Leaf.LEFT, 1.5, 5), EPS);
        assertEquals(1.0, PortalDoor.fromHinge(Leaf.RIGHT, 4, 5), EPS);
    }

    @Test
    void theFrontFaceShowsTheDoorAsLaidOut() {
        Tile tile = PortalDoor.tile(true, new Segment(0, 1, 0), 3, 4, 5);
        assertEquals(DoorPart.of(0, 3, 4, 5), tile.part());
        assertEquals(0.0, tile.uFrom(), EPS);
        assertEquals(1.0, tile.uTo(), EPS);
    }

    @Test
    void aMirroredPartRunsItsTextureBackwards() {
        Tile tile = PortalDoor.tile(true, new Segment(3, 4, 3), 3, 4, 5);
        assertEquals(DoorPart.FIELD_RIGHT, tile.part());
        assertEquals(1.0, tile.uFrom(), EPS);
        assertEquals(0.0, tile.uTo(), EPS);
    }

    /** From behind, the same cell shows its part's back, and the viewer reads the door the right way round. */
    @Test
    void theBackFaceIsTheDoorMirrored() {
        Tile back = PortalDoor.tile(false, new Segment(0, 1, 0), 3, 4, 5);
        assertEquals(DoorPart.of(0, 3, 4, 5).back(), back.part());
        assertEquals(DoorPart.FIELD_RIGHT, back.part());
        // FIELD_RIGHT is drawn mirrored, and seen from behind the cell runs the other way, so the two flips cancel
        assertEquals(0.0, back.uFrom(), EPS);
        assertEquals(1.0, back.uTo(), EPS);
    }

    @Test
    void halfACentreTileShowsHalfItsTexture() {
        Tile left = PortalDoor.tile(true, new Segment(2, 2.5, 2), 2, 5, 7);
        assertEquals(DoorPart.SEAM_CENTRE, left.part());
        assertEquals(0.0, left.uFrom(), EPS);
        assertEquals(0.5, left.uTo(), EPS);
        Tile leftBack = PortalDoor.tile(false, new Segment(2, 2.5, 2), 2, 5, 7);
        assertEquals(1.0, leftBack.uFrom(), EPS);
        assertEquals(0.5, leftBack.uTo(), EPS);
    }

    @Test
    void theGemSitsOnTheSeamInTheSeatRow() {
        assertEquals(2.5 - 2 / 16.0, PortalDoor.gemFrom(5), EPS);
        assertEquals(2.5 + 2 / 16.0, PortalDoor.gemTo(5), EPS);
        assertEquals(2.0 - 2 / 16.0, PortalDoor.gemFrom(4), EPS);
        // pixel rows 4 to 7 from the top of the seat row, row 1
        assertEquals(2.0 - 4 / 16.0, PortalDoor.gemTop(), EPS);
        assertEquals(2.0 - 8 / 16.0, PortalDoor.gemBottom(), EPS);
    }

    @Test
    void gemTextureRunsAcrossFromTheViewersLeft() {
        assertEquals(0.0, PortalDoor.gemU(true, PortalDoor.gemFrom(4), 4), EPS);
        assertEquals(1.0, PortalDoor.gemU(true, PortalDoor.gemTo(4), 4), EPS);
        assertEquals(1.0, PortalDoor.gemU(false, PortalDoor.gemFrom(4), 4), EPS);
        assertEquals(0.5, PortalDoor.gemU(false, 2.0, 4), EPS);
    }

    @Test
    void theReturnPortalHasNoDoorInAVault() {
        VaultPortalShape returnPortal = new VaultPortalShape(Axis.X, 0, 70, ReturnPortal.PLANE_Z, 2, 3);
        assertFalse(PortalDoor.hasDoor(true, returnPortal));
        assertTrue(PortalDoor.hasDoor(false, returnPortal));
        assertTrue(PortalDoor.hasDoor(true, new VaultPortalShape(Axis.X, 0, 70, ReturnPortal.PLANE_Z, 3, 3)));
        assertTrue(PortalDoor.hasDoor(true, new VaultPortalShape(Axis.Z, 0, 70, ReturnPortal.PLANE_Z, 2, 3)));
        assertTrue(PortalDoor.hasDoor(true, new VaultPortalShape(Axis.X, 5, 70, ReturnPortal.PLANE_Z, 2, 3)));
    }

    @Test
    void shadeMatchesVanillaOnTheAxesAndBlendsBetween() {
        assertEquals(1.0, PortalDoor.shade(0, 1, 0), EPS);
        assertEquals(0.5, PortalDoor.shade(0, -1, 0), EPS);
        assertEquals(0.8, PortalDoor.shade(0, 0, 1), EPS);
        assertEquals(0.6, PortalDoor.shade(-1, 0, 0), EPS);
        double r = Math.sqrt(0.5);
        assertEquals(0.7, PortalDoor.shade(r, 0, r), EPS);
    }
}
