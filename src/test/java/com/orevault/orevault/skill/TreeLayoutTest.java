package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.TreeLayout.Anchor;
import com.orevault.orevault.skill.TreeLayout.Box;
import com.orevault.orevault.skill.TreeLayout.ClusterGeometry;
import com.orevault.orevault.skill.TreeLayout.Point;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the Tome's hub placement (#136, #147).
 *
 * <p>The tree is a run of hubs, each with its cluster radiating around it (§8).
 * Almost everything that makes that legible is geometry rather than drawing, and
 * none of it is visible without launching a client, so each rule is a property
 * here instead:</p>
 *
 * <ul>
 *   <li>rings are disjoint annuli, so the strip between two of them is empty;</li>
 *   <li>boxes on a ring do not overlap in angle, so a node's own ray reaches it
 *       and nothing else;</li>
 *   <li>the corridor above and below every hub holds no box at any radius;</li>
 *   <li>and therefore no edge route crosses a box.</li>
 * </ul>
 *
 * <p>The three findings that opened #136 keep their tests too: radial distance
 * means prerequisite depth and nothing else, edges stop on borders rather than
 * centres, and a box is wide enough for every line it will ever draw.</p>
 */
class TreeLayoutTest {

    /**
     * Stands in for {@code Font#width}. Five pixels a character is close enough
     * to vanilla's proportional font for placement purposes, and being a pure
     * function of the name is what lets the width properties be asserted at all.
     */
    private static final ToIntFunction<NodeDef> WIDTH = def -> def.name().length() * 5;

    /**
     * The widest second line a node can draw, as the client measures it.
     *
     * <p>Mirrors {@code ResonanceTreeTab#widestLine} with the lang file's real
     * formats folded in — {@code "%s/%s  %s pt  Lv %s"} for a tier line — because
     * the point of #147's third finding is that the box has to fit the line it
     * draws, not the name it is called.</p>
     */
    private static final ToIntFunction<NodeDef> CONTENT = def -> {
        int widest = WIDTH.applyAsInt(def);
        for (int tier = 0; tier < def.maxTier(); tier++) {
            String line = tier + "/" + def.maxTier() + "  " + def.costs()[tier] + " pt  Lv "
                    + def.levelReqs()[tier];
            widest = Math.max(widest, line.length() * 5);
        }
        if (def.nodeClass() == NodeClass.FORK_PARENT) {
            for (NodeDef option : NodeDefs.forkOptions(def.id())) {
                widest = Math.max(widest, option.name().length() * 5);
            }
        }
        return widest;
    };

    private static List<NodeDef> resonance() {
        return NodeDefs.getByTree(Tree.RESONANCE);
    }

    private static TreeLayout.Layout layout() {
        return TreeLayout.of(resonance(), CONTENT);
    }

    private static NodeDef node(String id, Cluster cluster, String... prereqIds) {
        List<Prereq> prereqs = new ArrayList<>();
        for (String prereqId : prereqIds) {
            prereqs.add(new Prereq(prereqId, 1));
        }
        return new NodeDef(id, id, Tree.RESONANCE, cluster, NodeClass.SMALL, null, null,
                new int[] {1}, new int[] {0}, prereqs, null, false);
    }

    // ----- placement -----

    @Test
    void everyNodeIsPlacedExactlyOnce() {
        TreeLayout.Layout layout = layout();
        assertEquals(resonance().size(), layout.boxes().size());
        for (NodeDef def : resonance()) {
            assertNotNull(layout.box(def.id()), def.id() + " was not placed");
        }
    }

    @Test
    void noTwoBoxesOverlap() {
        List<Box> boxes = new ArrayList<>(layout().boxes().values());
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                boolean apart = a.right() <= b.x() || b.right() <= a.x()
                        || a.bottom() <= b.y() || b.bottom() <= a.y();
                assertTrue(apart, a.nodeId() + " overlaps " + b.nodeId());
            }
        }
    }

    @Test
    void noBoxOverlapsAHub() {
        TreeLayout.Layout layout = layout();
        for (Anchor anchor : layout.anchors()) {
            for (Box box : layout.boxes().values()) {
                boolean apart = anchor.right() <= box.x() || box.right() <= anchor.x()
                        || anchor.bottom() <= box.y() || box.bottom() <= anchor.y();
                assertTrue(apart, box.nodeId() + " overlaps the " + anchor.cluster() + " hub");
            }
        }
    }

    @Test
    void everyClusterGetsOneHubAtTheCentreOfItsOwnNodes() {
        TreeLayout.Layout layout = layout();
        Map<Cluster, Anchor> seen = new HashMap<>();
        for (Anchor anchor : layout.anchors()) {
            assertNull(seen.put(anchor.cluster(), anchor),
                    anchor.cluster() + " has more than one hub");
        }
        for (Box box : layout.boxes().values()) {
            Anchor hub = seen.get(box.cluster());
            assertNotNull(hub, box.cluster() + " has no hub");
            ClusterGeometry geometry = layout.geometry(box.cluster());
            double distance = Math.hypot(box.centerX() - hub.centerX(), box.centerY() - hub.centerY());
            assertTrue(distance <= geometry.outerRadius(),
                    box.nodeId() + " sits outside its own cluster's circle");
        }
    }

    @Test
    void hubsRunDownOneSpineInClusterOrder() {
        List<Anchor> anchors = layout().anchors();
        for (int i = 1; i < anchors.size(); i++) {
            assertEquals(anchors.get(0).centerX(), anchors.get(i).centerX(),
                    anchors.get(i).cluster() + " is off the spine");
            assertTrue(anchors.get(i).centerY() > anchors.get(i - 1).centerY(),
                    anchors.get(i).cluster() + " is not below " + anchors.get(i - 1).cluster());
        }
    }

    @Test
    void consecutiveClustersClearEachOther() {
        TreeLayout.Layout layout = layout();
        List<Anchor> anchors = layout.anchors();
        for (int i = 1; i < anchors.size(); i++) {
            ClusterGeometry above = layout.geometry(anchors.get(i - 1).cluster());
            ClusterGeometry below = layout.geometry(anchors.get(i).cluster());
            double separation = below.centerY() - above.centerY()
                    - above.outerRadius() - below.outerRadius();
            assertTrue(separation >= TreeLayout.CLUSTER_GAP - 1,
                    above.cluster() + " and " + below.cluster() + " are only " + separation + " apart");
        }
    }

    @Test
    void anchorsCarryTheirPointsSpentGate() {
        for (Anchor anchor : layout().anchors()) {
            assertEquals(NodeDefs.anchorGate(anchor.cluster()), anchor.gate());
        }
    }

    // ----- the properties the routing depends on -----

    @Test
    void aNodesRingIsItsPrerequisiteDepthInsideItsCluster() {
        TreeLayout.Layout layout = layout();
        for (NodeDef def : resonance()) {
            Box box = layout.box(def.id());
            int expected = 1;
            for (Prereq prereq : def.prereqs()) {
                NodeDef other = NodeDefs.get(prereq.nodeId());
                if (other != null && other.cluster() == def.cluster()) {
                    expected = Math.max(expected, layout.box(prereq.nodeId()).ring() + 1);
                }
            }
            if (def.forkParentId() != null) {
                expected = Math.max(expected, layout.box(def.forkParentId()).ring() + 1);
            }
            assertEquals(expected, box.ring(),
                    def.id() + " is not on the ring its prerequisite depth puts it on");
        }
    }

    @Test
    void nothingSitsInTheCorridorAboveOrBelowAHub() {
        TreeLayout.Layout layout = layout();
        double limit = Math.toRadians(90 - TreeLayout.SPINE_HALF_DEG);
        for (Box box : layout.boxes().values()) {
            ClusterGeometry hub = layout.geometry(box.cluster());
            for (int cx : new int[] {box.x(), box.right()}) {
                for (int cy : new int[] {box.y(), box.bottom()}) {
                    double angle = Math.atan2(hub.centerY() - cy, cx - hub.centerX());
                    double fromHorizontal = Math.abs(Math.atan2(Math.sin(angle), Math.cos(angle)));
                    fromHorizontal = Math.min(fromHorizontal, Math.PI - fromHorizontal);
                    assertTrue(fromHorizontal <= limit + 1e-6,
                            box.nodeId() + " reaches into its hub's corridor");
                }
            }
        }
    }

    @Test
    void ringsAreDisjointAnnuli() {
        TreeLayout.Layout layout = layout();
        for (Anchor anchor : layout.anchors()) {
            ClusterGeometry hub = layout.geometry(anchor.cluster());
            List<TreeLayout.Ring> rings = hub.rings();
            for (int i = 1; i < rings.size(); i++) {
                if (rings.get(i).inner() == rings.get(i).outer()) {
                    continue; // a ring nothing landed on
                }
                assertTrue(rings.get(i).inner() >= rings.get(i - 1).outer() + TreeLayout.RING_GAP - 1e-6,
                        anchor.cluster() + " ring " + i + " overlaps the one inside it");
            }
        }
        for (Box box : layout.boxes().values()) {
            ClusterGeometry hub = layout.geometry(box.cluster());
            TreeLayout.Ring ring = hub.ring(box.ring());
            double nearest = Double.MAX_VALUE;
            double furthest = 0;
            for (int cx : new int[] {box.x(), box.right()}) {
                for (int cy : new int[] {box.y(), box.bottom()}) {
                    double distance = Math.hypot(cx - hub.centerX(), cy - hub.centerY());
                    furthest = Math.max(furthest, distance);
                }
            }
            nearest = nearestDistance(box, hub);
            assertTrue(nearest >= ring.inner() - 2 && furthest <= ring.outer() + 2,
                    box.nodeId() + " sticks out of its own ring's band");
        }
    }

    @Test
    void boxesOnARingDoNotOverlapInAngle() {
        TreeLayout.Layout layout = layout();
        List<Box> boxes = new ArrayList<>(layout.boxes().values());
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                if (a.cluster() != b.cluster() || a.ring() != b.ring()) {
                    continue;
                }
                ClusterGeometry hub = layout.geometry(a.cluster());
                double gap = Math.abs(deviation(b.angle(), a.angle()))
                        - angularHalfWidth(a, hub) - angularHalfWidth(b, hub);
                assertTrue(gap > 0, a.nodeId() + " and " + b.nodeId()
                        + " overlap in angle on ring " + a.ring());
            }
        }
    }

    // ----- edges -----

    @Test
    void noEdgeCrossesANodeBox() {
        TreeLayout.Layout layout = layout();
        for (NodeDef def : resonance()) {
            Box to = layout.box(def.id());
            for (Prereq prereq : def.prereqs()) {
                Box from = layout.box(prereq.nodeId());
                if (from != null) {
                    assertRouteIsClear(layout, TreeLayout.edge(layout, from, to), from, to);
                }
            }
            if (def.forkParentId() != null) {
                Box from = layout.box(def.forkParentId());
                if (from != null) {
                    assertRouteIsClear(layout, TreeLayout.edge(layout, from, to), from, to);
                }
            }
            assertRouteIsClear(layout, TreeLayout.spoke(layout, to), to, to);
        }
    }

    @Test
    void everyEdgeStartsAndEndsOnABoxBorder() {
        TreeLayout.Layout layout = layout();
        for (NodeDef def : resonance()) {
            Box to = layout.box(def.id());
            for (Prereq prereq : def.prereqs()) {
                Box from = layout.box(prereq.nodeId());
                if (from == null) {
                    continue;
                }
                List<Point> route = TreeLayout.edge(layout, from, to);
                assertOnBorder(from, route.getFirst());
                assertOnBorder(to, route.getLast());
            }
        }
    }

    @Test
    void everyFirstRingNodeIsJoinedToItsHub() {
        TreeLayout.Layout layout = layout();
        int spokes = 0;
        for (Box box : layout.boxes().values()) {
            List<Point> spoke = TreeLayout.spoke(layout, box);
            if (box.ring() != 1) {
                assertTrue(spoke.isEmpty(), box.nodeId() + " is not on the first ring");
                continue;
            }
            spokes++;
            assertEquals(2, spoke.size());
            assertOnBorder(box, spoke.getLast());
            Anchor hub = hubOf(layout, box.cluster());
            Point start = spoke.getFirst();
            assertTrue(Math.abs(start.x() - hub.centerX()) <= hub.width() / 2 + 1
                            && Math.abs(start.y() - hub.centerY()) <= hub.height() / 2 + 1,
                    box.nodeId() + "'s spoke does not start on its hub");
        }
        assertTrue(spokes > 0, "no node is on a first ring at all");
    }

    @Test
    void theSpineJoinsConsecutiveHubs() {
        TreeLayout.Layout layout = layout();
        List<Anchor> anchors = layout.anchors();
        for (int i = 1; i < anchors.size(); i++) {
            List<Point> spine = TreeLayout.spine(layout,
                    anchors.get(i - 1).cluster(), anchors.get(i).cluster());
            assertEquals(2, spine.size());
            assertEquals(spine.getFirst().x(), spine.getLast().x(), "the spine is not vertical");
            assertTrue(spine.getLast().y() > spine.getFirst().y());
        }
    }

    // ----- box sizing -----

    @Test
    void boxesWidenToFitTheirContentWithinBounds() {
        List<NodeDef> nodes = List.of(
                node("short", Cluster.PROSPECTING),
                node("a_considerably_longer_node_name_than_that_one", Cluster.PROSPECTING));
        TreeLayout.Layout layout = TreeLayout.of(nodes, CONTENT);

        Box small = layout.box("short");
        Box large = layout.box("a_considerably_longer_node_name_than_that_one");
        assertEquals(Math.max(TreeLayout.MIN_NODE_WIDTH,
                CONTENT.applyAsInt(nodes.get(0)) + TreeLayout.TEXT_PADDING * 2), small.width());
        assertTrue(large.width() > small.width());
        assertEquals(TreeLayout.MAX_NODE_WIDTH, large.width(), "the cap did not bite");
    }

    @Test
    void noRealNodeIsForcedToTruncateAnyLineItDraws() {
        for (NodeDef def : resonance()) {
            int needed = CONTENT.applyAsInt(def) + TreeLayout.TEXT_PADDING * 2;
            assertTrue(needed <= TreeLayout.MAX_NODE_WIDTH,
                    def.id() + " needs " + needed + "px, which is past the "
                            + TreeLayout.MAX_NODE_WIDTH + "px cap");
        }
    }

    @Test
    void aBoxIsWideEnoughForItsTierLineNotOnlyItsName() {
        // A three-character name whose tier line is far longer than it is. The
        // grid measured the name alone, so this is the shape that overflowed.
        NodeDef def = new NodeDef("ab", "ab", Tree.RESONANCE, Cluster.PROSPECTING, NodeClass.SMALL,
                null, null, new int[] {12345}, new int[] {30}, List.of(), null, false);
        TreeLayout.Layout layout = TreeLayout.of(List.of(def), CONTENT);
        assertTrue(layout.box("ab").width() >= CONTENT.applyAsInt(def) + TreeLayout.TEXT_PADDING * 2,
                "the box was measured from the name rather than from what it draws");
    }

    // ----- forks -----

    @Test
    void forkOptionsSitOnTheRingOutsideTheirParent() {
        TreeLayout.Layout layout = layout();
        int checked = 0;
        for (NodeDef def : resonance()) {
            if (def.nodeClass() != NodeClass.FORK_PARENT) {
                continue;
            }
            Box parent = layout.box(def.id());
            for (NodeDef option : NodeDefs.forkOptions(def.id())) {
                Box box = layout.box(option.id());
                assertNotNull(box, option.id() + " was not placed");
                assertEquals(parent.ring() + 1, box.ring(),
                        option.id() + " is not on the ring outside its parent");
                assertEquals(parent.cluster(), box.cluster());
                checked++;
            }
        }
        assertTrue(checked > 0, "no fork options in the tree to check");
    }

    @Test
    void forkOptionsStayOnTheirParentsSideOfTheSpine() {
        TreeLayout.Layout layout = layout();
        for (NodeDef def : resonance()) {
            if (def.nodeClass() != NodeClass.FORK_PARENT) {
                continue;
            }
            Box parent = layout.box(def.id());
            for (NodeDef option : NodeDefs.forkOptions(def.id())) {
                Box box = layout.box(option.id());
                assertEquals(Math.signum(Math.cos(parent.angle())),
                        Math.signum(Math.cos(box.angle())),
                        option.id() + " is on the other side of the spine from its parent");
            }
        }
    }

    // ----- edge cases -----

    @Test
    void prerequisiteOutsideTheGivenSetDoesNotPlaceAGhostNode() {
        List<NodeDef> nodes = List.of(node("kept", Cluster.PROSPECTING, "never_given"));
        TreeLayout.Layout layout = TreeLayout.of(nodes, CONTENT);

        assertEquals(1, layout.boxes().size());
        assertNotNull(layout.box("kept"));
        assertNull(layout.box("never_given"));
    }

    @Test
    void aPrerequisiteCycleTerminatesInsteadOfOverflowingTheStack() {
        List<NodeDef> nodes = List.of(
                node("a", Cluster.PROSPECTING, "b"),
                node("b", Cluster.PROSPECTING, "a"));
        TreeLayout.Layout layout = TreeLayout.of(nodes, CONTENT);

        assertEquals(2, layout.boxes().size());
        assertNotNull(layout.box("a"));
        assertNotNull(layout.box("b"));
    }

    @Test
    void emptyInputProducesAnEmptyLayout() {
        TreeLayout.Layout layout = TreeLayout.of(List.of(), CONTENT);
        assertTrue(layout.anchors().isEmpty());
        assertTrue(layout.boxes().isEmpty());
        assertEquals(0, layout.width());
        assertEquals(0, layout.height());
    }

    @Test
    void aCrowdedRingGrowsRatherThanColliding() {
        List<NodeDef> nodes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            nodes.add(node("independent_node_number_" + i, Cluster.PROSPECTING));
        }
        TreeLayout.Layout layout = TreeLayout.of(nodes, CONTENT);

        assertEquals(40, layout.boxes().size());
        List<Box> boxes = new ArrayList<>(layout.boxes().values());
        for (Box box : boxes) {
            assertEquals(1, box.ring(), "independent nodes belong on the first ring");
        }
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                assertTrue(a.right() <= b.x() || b.right() <= a.x()
                                || a.bottom() <= b.y() || b.bottom() <= a.y(),
                        a.nodeId() + " overlaps " + b.nodeId() + " on a crowded ring");
            }
        }
    }

    @Test
    void everyBoxSitsInsideTheReportedContentSize() {
        TreeLayout.Layout layout = layout();
        for (Box box : layout.boxes().values()) {
            assertTrue(box.x() >= 0 && box.y() >= 0, box.nodeId() + " is off the top-left");
            assertTrue(box.right() <= layout.width(), box.nodeId() + " runs past the reported width");
            assertTrue(box.bottom() <= layout.height(), box.nodeId() + " runs past the reported height");
        }
        for (Anchor anchor : layout.anchors()) {
            assertTrue(anchor.x() >= 0 && anchor.right() <= layout.width());
        }
    }

    @Test
    void theGuttersHoldNothing() {
        TreeLayout.Layout layout = layout();
        for (Box box : layout.boxes().values()) {
            assertTrue(box.x() >= TreeLayout.GUTTER,
                    box.nodeId() + " reaches into the left gutter");
            assertTrue(box.right() <= layout.width() - TreeLayout.GUTTER,
                    box.nodeId() + " reaches into the right gutter");
        }
    }

    // ----- helpers -----

    private static Anchor hubOf(TreeLayout.Layout layout, Cluster cluster) {
        for (Anchor anchor : layout.anchors()) {
            if (anchor.cluster() == cluster) {
                return anchor;
            }
        }
        throw new AssertionError("no hub for " + cluster);
    }

    /**
     * Walks a route pixel by pixel and fails if it enters any box it does not
     * belong to.
     *
     * <p>The endpoints deliberately sit on their own boxes' borders, so those two
     * are excluded; everything else on the page is fair game. This is the assertion
     * that the whole ring-and-corridor geometry exists to make true.</p>
     */
    private static void assertRouteIsClear(TreeLayout.Layout layout, List<Point> route, Box from, Box to) {
        for (int i = 0; i + 1 < route.size(); i++) {
            Point a = route.get(i);
            Point b = route.get(i + 1);
            int steps = Math.max(1, Math.max(Math.abs(b.x() - a.x()), Math.abs(b.y() - a.y())));
            for (int step = 0; step <= steps; step++) {
                int px = a.x() + (b.x() - a.x()) * step / steps;
                int py = a.y() + (b.y() - a.y()) * step / steps;
                for (Box box : layout.boxes().values()) {
                    if (box.nodeId().equals(from.nodeId()) || box.nodeId().equals(to.nodeId())) {
                        continue;
                    }
                    assertFalse(box.contains(px, py),
                            "the edge " + from.nodeId() + " -> " + to.nodeId()
                                    + " crosses " + box.nodeId() + " at " + px + "," + py);
                }
            }
        }
    }

    /** An endpoint must land on the box's frame, not inside it and not adrift of it. */
    private static void assertOnBorder(Box box, Point point) {
        boolean withinX = point.x() >= box.x() - 1 && point.x() <= box.right() + 1;
        boolean withinY = point.y() >= box.y() - 1 && point.y() <= box.bottom() + 1;
        boolean onVertical = Math.abs(point.x() - box.x()) <= 1 || Math.abs(point.x() - box.right()) <= 1;
        boolean onHorizontal = Math.abs(point.y() - box.y()) <= 1
                || Math.abs(point.y() - box.bottom()) <= 1;
        assertTrue(withinX && withinY && (onVertical || onHorizontal),
                "the edge at " + box.nodeId() + " does not stop on the box border: " + point);
    }

    private static double nearestDistance(Box box, ClusterGeometry hub) {
        double dx = Math.max(0, Math.max(box.x() - hub.centerX(), hub.centerX() - box.right()));
        double dy = Math.max(0, Math.max(box.y() - hub.centerY(), hub.centerY() - box.bottom()));
        return Math.hypot(dx, dy);
    }

    private static double angularHalfWidth(Box box, ClusterGeometry hub) {
        double worst = 0;
        for (int cx : new int[] {box.x(), box.right()}) {
            for (int cy : new int[] {box.y(), box.bottom()}) {
                double angle = Math.atan2(hub.centerY() - cy, cx - hub.centerX());
                worst = Math.max(worst, Math.abs(deviation(angle, box.angle())));
            }
        }
        return worst;
    }

    private static double deviation(double angle, double reference) {
        return Math.atan2(Math.sin(angle - reference), Math.cos(angle - reference));
    }
}
