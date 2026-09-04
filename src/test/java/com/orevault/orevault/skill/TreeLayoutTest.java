package com.orevault.orevault.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic tests for the Tome's node-graph placement ([35]).
 *
 * <p>The two properties that matter to a reader of the screen are that no two
 * nodes are dealt the same cell, and that a node sits below its prerequisites
 * inside its own branch. Both are easy to break by accident when nodes are
 * added, and neither is visible without launching a client — which is exactly
 * the case for testing them here.</p>
 */
class TreeLayoutTest {

    private static NodeDef node(String id, String branch, String... prereqIds) {
        List<Prereq> prereqs = new ArrayList<>();
        for (String prereqId : prereqIds) {
            prereqs.add(new Prereq(prereqId, 1));
        }
        return new NodeDef(id, id, Tree.RESONANCE, branch,
                new int[] {1}, new int[] {0}, prereqs, false, null, false);
    }

    // ----- the properties the screen depends on -----

    @Test
    void everyNodeGetsItsOwnCell() {
        TreeLayout.Layout layout = TreeLayout.of(NodeDefs.getByTree(Tree.RESONANCE));

        Set<String> occupied = new HashSet<>();
        for (NodeDef def : NodeDefs.getByTree(Tree.RESONANCE)) {
            TreeLayout.Cell cell = layout.cell(def.id());
            assertNotNull(cell, def.id() + " was not placed");
            assertTrue(occupied.add(cell.column() + "," + cell.row()),
                    def.id() + " collides at column " + cell.column() + " row " + cell.row());
        }
    }

    @Test
    void aNodeSitsBelowItsPrerequisitesInTheSameBranch() {
        TreeLayout.Layout layout = TreeLayout.of(NodeDefs.getByTree(Tree.RESONANCE));

        for (NodeDef def : NodeDefs.getByTree(Tree.RESONANCE)) {
            TreeLayout.Cell cell = layout.cell(def.id());
            for (Prereq prereq : def.prereqs()) {
                TreeLayout.Cell from = layout.cell(prereq.nodeId());
                if (from == null || from.column() != cell.column()) {
                    continue; // cross-branch edges are drawn, not ordered
                }
                assertTrue(from.row() < cell.row(),
                        def.id() + " is drawn above its prerequisite " + prereq.nodeId());
            }
        }
    }

    @Test
    void columnsFollowBranchOrderInNodeDefs() {
        List<NodeDef> nodes = NodeDefs.getByTree(Tree.RESONANCE);
        TreeLayout.Layout layout = TreeLayout.of(nodes);

        List<String> firstAppearance = new ArrayList<>();
        for (NodeDef def : nodes) {
            if (!firstAppearance.contains(def.branch())) {
                firstAppearance.add(def.branch());
            }
        }
        assertEquals(firstAppearance, layout.branches());
        assertEquals(firstAppearance.size(), layout.columnCount());
    }

    // ----- behaviour on sets the caller has filtered -----

    @Test
    void prerequisiteOutsideTheGivenSetDoesNotPlaceAGhostNode() {
        // What the Ultimine filter produces: a visible node whose prerequisite
        // was removed from the list before layout.
        TreeLayout.Layout layout = TreeLayout.of(List.of(
                node("visible", "Ultimine", "hidden")));

        assertNull(layout.cell("hidden"));
        assertEquals(1, layout.cells().size());
        assertEquals(0, layout.cell("visible").row());
    }

    @Test
    void deeperNodesSortBelowShallowOnesInTheSameBranch() {
        TreeLayout.Layout layout = TreeLayout.of(List.of(
                node("third", "B", "second"),
                node("first", "B"),
                node("second", "B", "first")));

        assertEquals(0, layout.cell("first").row());
        assertEquals(1, layout.cell("second").row());
        assertEquals(2, layout.cell("third").row());
    }

    @Test
    void rootsOfTheSameBranchShareADepthButNotACell() {
        TreeLayout.Layout layout = TreeLayout.of(List.of(
                node("a", "Utility"), node("b", "Utility"), node("c", "Utility")));

        assertEquals(Set.of(0, 1, 2), Set.of(
                layout.cell("a").row(), layout.cell("b").row(), layout.cell("c").row()));
        assertEquals(3, layout.rowCount());
    }

    @Test
    void aPrerequisiteCycleTerminatesInsteadOfOverflowingTheStack() {
        // Not a state NodeDefs can reach today. It is asserted anyway because
        // the failure mode is a client crash on opening the Tome, and the guard
        // that prevents it is invisible until someone deletes it.
        TreeLayout.Layout layout = TreeLayout.of(List.of(
                node("x", "Loop", "y"),
                node("y", "Loop", "x")));

        assertEquals(2, layout.cells().size());
        assertNotNull(layout.cell("x"));
        assertNotNull(layout.cell("y"));
    }

    @Test
    void emptyInputProducesAnEmptyGrid() {
        TreeLayout.Layout layout = TreeLayout.of(List.of());

        assertEquals(0, layout.columnCount());
        assertEquals(0, layout.rowCount());
        assertTrue(layout.branches().isEmpty());
    }
}
