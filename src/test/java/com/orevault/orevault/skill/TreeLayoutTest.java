package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.TreeLayout.Box;
import com.orevault.orevault.skill.TreeLayout.Point;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the Tome's cluster/stagger placement (#136).
 *
 * <p>#36's grid was rejected on playtest for three things a unit test can pin:
 * vertical position implied prerequisites that did not exist, prerequisite
 * edges ran to node centres and crossed the boxes, and fixed-width boxes
 * truncated longer names. Each has a property here, because none of them is
 * visible without launching a client.</p>
 */
class TreeLayoutTest {

    /**
     * Stands in for {@code Font#width}. Five pixels a character is close enough
     * to vanilla's proportional font for placement purposes, and being a pure
     * function of the name is what lets the width properties be asserted at all.
     */
    private static final ToIntFunction<NodeDef> WIDTH = def -> def.name().length() * 5;

    private static List<NodeDef> resonance() {
        return NodeDefs.getByTree(Tree.RESONANCE);
    }

    private static TreeLayout.Layout layout() {
        return TreeLayout.of(resonance(), WIDTH);
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

        for (NodeDef def : resonance()) {
            assertNotNull(layout.box(def.id()), def.id() + " was not placed");
        }
        assertEquals(resonance().size(), layout.boxes().size());
    }

    @Test
    void noTwoBoxesOverlap() {
        List<Box> boxes = new ArrayList<>(layout().boxes().values());

        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i);
                Box b = boxes.get(j);
                assertFalse(a.x() < b.right() && b.x() < a.right()
                                && a.y() < b.bottom() && b.y() < a.bottom(),
                        a.nodeId() + " overlaps " + b.nodeId());
            }
        }
    }

    @Test
    void everyClusterGetsOneAnchorAboveItsNodes() {
        TreeLayout.Layout layout = layout();

        List<Cluster> clusterOrder = new ArrayList<>();
        for (NodeDef def : resonance()) {
            if (!clusterOrder.contains(def.cluster())) {
                clusterOrder.add(def.cluster());
            }
        }
        assertEquals(clusterOrder, layout.anchors().stream().map(TreeLayout.Anchor::cluster).toList());

        for (TreeLayout.Anchor anchor : layout.anchors()) {
            for (Box box : layout.boxes().values()) {
                if (box.cluster() == anchor.cluster()) {
                    assertTrue(anchor.band() < box.band(),
                            box.nodeId() + " is drawn above its own cluster anchor");
                }
            }
        }
    }

    @Test
    void anchorsCarryTheirPointsSpentGate() {
        for (TreeLayout.Anchor anchor : layout().anchors()) {
            assertEquals(NodeDefs.anchorGate(anchor.cluster()), anchor.gate());
        }
    }

    @Test
    void anchorsSpanTheWholeLaneAreaSoTheyReadAsHeadings() {
        TreeLayout.Layout layout = layout();

        for (TreeLayout.Anchor anchor : layout.anchors()) {
            for (Box box : layout.boxes().values()) {
                assertTrue(anchor.width() > box.width(),
                        "anchor " + anchor.cluster() + " is no wider than node " + box.nodeId());
            }
        }
    }

    @Test
    void aNodeIsDrawnBelowEveryPrerequisiteInItsOwnCluster() {
        TreeLayout.Layout layout = layout();

        for (NodeDef def : resonance()) {
            Box box = layout.box(def.id());
            for (Prereq prereq : def.prereqs()) {
                Box from = layout.box(prereq.nodeId());
                if (from == null || from.cluster() != box.cluster()) {
                    continue; // cross-cluster edges are drawn, not ordered
                }
                assertTrue(from.band() < box.band(),
                        def.id() + " is not drawn below its prerequisite " + prereq.nodeId());
            }
        }
    }

    // ----- the rejection reasons from #136 -----

    @Test
    void nothingSitsDirectlyBelowANodeItDoesNotRequire() {
        // #36's first rejection: Gravel Purge sat under Common Ore Boost while
        // both were available from the start, and vertical position read as a
        // chain. Two boxes may only be vertically adjacent if one really does
        // require the other.
        TreeLayout.Layout layout = layout();

        for (Box lower : layout.boxes().values()) {
            for (Box upper : layout.boxes().values()) {
                if (upper.band() + 1 != lower.band()) {
                    continue;
                }
                if (upper.x() >= lower.right() || lower.x() >= upper.right()) {
                    continue; // no horizontal overlap, so nothing is implied
                }
                assertTrue(requires(lower.nodeId(), upper.nodeId()),
                        lower.nodeId() + " is drawn directly under " + upper.nodeId()
                                + ", which it does not require");
            }
        }
    }

    @Test
    void boxesWidenToFitTheirNameWithinBounds() {
        TreeLayout.Layout layout = layout();

        for (NodeDef def : resonance()) {
            Box box = layout.box(def.id());
            assertTrue(box.width() >= TreeLayout.MIN_NODE_WIDTH,
                    def.id() + " is narrower than the minimum");
            assertTrue(box.width() <= TreeLayout.MAX_NODE_WIDTH,
                    def.id() + " is wider than the maximum");
            int wanted = WIDTH.applyAsInt(def) + TreeLayout.TEXT_PADDING * 2;
            if (wanted > TreeLayout.MIN_NODE_WIDTH && wanted < TreeLayout.MAX_NODE_WIDTH) {
                assertEquals(wanted, box.width(),
                        def.id() + " does not size to its name");
            }
        }
    }

    @Test
    void noRealNodeNameIsForcedToTruncate() {
        // The maximum exists to stop one pathological name stretching a lane,
        // not to truncate the names actually in the tree. If a new node ever
        // needs more room than the cap, this fails and the cap gets raised
        // rather than the name getting an ellipsis.
        for (NodeDef def : resonance()) {
            assertTrue(WIDTH.applyAsInt(def) + TreeLayout.TEXT_PADDING * 2 <= TreeLayout.MAX_NODE_WIDTH,
                    def.name() + " does not fit inside the maximum box width");
        }
    }

    @Test
    void noEdgeCrossesANodeBox() {
        // #36's second rejection. Edges are routed through band gaps and the
        // outer gutters, both of which are empty by construction, so this is a
        // property of the routing rather than of the current node set.
        TreeLayout.Layout layout = layout();

        for (NodeDef def : resonance()) {
            Box to = layout.box(def.id());
            for (Prereq prereq : def.prereqs()) {
                Box from = layout.box(prereq.nodeId());
                if (from == null) {
                    continue;
                }
                List<Point> route = TreeLayout.edge(layout, from, to);
                for (int i = 0; i + 1 < route.size(); i++) {
                    for (Box box : layout.boxes().values()) {
                        if (box.nodeId().equals(from.nodeId()) || box.nodeId().equals(to.nodeId())) {
                            continue;
                        }
                        assertFalse(crosses(route.get(i), route.get(i + 1), box),
                                "the edge " + prereq.nodeId() + " -> " + def.id()
                                        + " crosses " + box.nodeId());
                    }
                }
            }
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
                assertTrue(route.size() >= 2, "empty route for " + prereq.nodeId() + " -> " + def.id());
                assertTrue(onBorder(route.get(0), from),
                        "edge from " + prereq.nodeId() + " does not start on its border");
                assertTrue(onBorder(route.get(route.size() - 1), to),
                        "edge to " + def.id() + " does not end on its border");
            }
        }
    }

    // ----- forks -----

    @Test
    void forkOptionsSitSideBySideDirectlyUnderTheirParent() {
        TreeLayout.Layout layout = layout();

        for (NodeDef def : resonance()) {
            if (def.nodeClass() != NodeClass.FORK_PARENT) {
                continue;
            }
            Box parent = layout.box(def.id());
            List<NodeDef> options = NodeDefs.forkOptions(def.id());
            assertFalse(options.isEmpty(), def.id() + " is a fork parent with no options");

            int band = -1;
            for (NodeDef option : options) {
                Box box = layout.box(option.id());
                assertEquals(parent.band() + 1, box.band(),
                        option.id() + " is not in the band directly under " + def.id());
                if (band == -1) {
                    band = box.band();
                }
            }
        }
    }

    @Test
    void forkOptionsAreCentredOnTheirParent() {
        TreeLayout.Layout layout = layout();

        for (NodeDef def : resonance()) {
            if (def.nodeClass() != NodeClass.FORK_PARENT) {
                continue;
            }
            Box parent = layout.box(def.id());
            List<Box> options = NodeDefs.forkOptions(def.id()).stream()
                    .map(option -> layout.box(option.id()))
                    .sorted((a, b) -> Integer.compare(a.x(), b.x()))
                    .toList();

            int spanLeft = options.get(0).x();
            int spanRight = options.get(options.size() - 1).right();
            int spanCentre = (spanLeft + spanRight) / 2;
            // A lane apart at most: options are placed in whole lanes, so an
            // even number of them cannot be centred exactly.
            assertTrue(Math.abs(spanCentre - parent.centerX()) <= TreeLayout.MAX_NODE_WIDTH,
                    def.id() + "'s options are not centred under it");
        }
    }

    // ----- behaviour on sets the caller has filtered -----

    @Test
    void prerequisiteOutsideTheGivenSetDoesNotPlaceAGhostNode() {
        // What the Ultimine filter produces: a visible node whose prerequisite
        // was removed from the list before layout.
        TreeLayout.Layout layout = TreeLayout.of(
                List.of(node("visible", Cluster.BROAD_CUT, "hidden")), WIDTH);

        assertNull(layout.box("hidden"));
        assertEquals(1, layout.boxes().size());
    }

    @Test
    void aPrerequisiteCycleTerminatesInsteadOfOverflowingTheStack() {
        // Not a state NodeDefs can reach today. It is asserted anyway because
        // the failure mode is a client crash on opening the Tome, and the guard
        // that prevents it is invisible until someone deletes it.
        TreeLayout.Layout layout = TreeLayout.of(List.of(
                node("x", Cluster.MASTERY, "y"),
                node("y", Cluster.MASTERY, "x")), WIDTH);

        assertEquals(2, layout.boxes().size());
        assertNotNull(layout.box("x"));
        assertNotNull(layout.box("y"));
    }

    @Test
    void emptyInputProducesAnEmptyLayout() {
        TreeLayout.Layout layout = TreeLayout.of(List.of(), WIDTH);

        assertTrue(layout.anchors().isEmpty());
        assertTrue(layout.boxes().isEmpty());
        assertEquals(0, layout.height());
    }

    @Test
    void theTreeIsAColumnRatherThanASheet() {
        // Three lanes is a deliberate trade: a tall narrow tree scrolls with the
        // cluster order instead of across it, and fits a normal GUI scale. If a
        // change ever makes the tree wider than it is tall, the scroll gesture
        // stops matching the reading order and this is the cheapest place to
        // notice.
        TreeLayout.Layout layout = layout();

        assertTrue(layout.width() <= 600,
                "the tree is " + layout.width() + " wide, which will not fit a Tome page");
        assertTrue(layout.height() > layout.width(),
                "the tree is wider (" + layout.width() + ") than it is tall (" + layout.height() + ")");
    }

    @Test
    void everyBoxSitsInsideTheReportedContentSize() {
        TreeLayout.Layout layout = layout();

        for (Box box : layout.boxes().values()) {
            assertTrue(box.x() >= 0 && box.right() <= layout.width(),
                    box.nodeId() + " is outside the content width");
            assertTrue(box.y() >= 0 && box.bottom() <= layout.height(),
                    box.nodeId() + " is outside the content height");
        }
    }

    // ----- helpers -----

    private static boolean requires(String nodeId, String candidateId) {
        NodeDef def = NodeDefs.get(nodeId);
        if (def == null) {
            return false;
        }
        if (candidateId.equals(def.forkParentId())) {
            return true;
        }
        for (Prereq prereq : def.prereqs()) {
            if (prereq.nodeId().equals(candidateId)) {
                return true;
            }
        }
        return false;
    }

    /** Whether an axis-aligned segment passes through a box's interior. */
    private static boolean crosses(Point a, Point b, Box box) {
        int minX = Math.min(a.x(), b.x());
        int maxX = Math.max(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int maxY = Math.max(a.y(), b.y());
        return minX < box.right() && box.x() < maxX + 1
                && minY < box.bottom() && box.y() < maxY + 1;
    }

    private static boolean onBorder(Point point, Box box) {
        boolean withinX = point.x() >= box.x() && point.x() <= box.right();
        boolean withinY = point.y() >= box.y() && point.y() <= box.bottom();
        boolean onVerticalEdge = (point.x() == box.x() || point.x() == box.right()) && withinY;
        boolean onHorizontalEdge = (point.y() == box.y() || point.y() == box.bottom()) && withinX;
        return onVerticalEdge || onHorizontalEdge;
    }
}
