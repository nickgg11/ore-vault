package com.orevault.orevault.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.orevault.orevault.skill.NodeDef.Prereq;

/**
 * Places skill-tree nodes on a grid for the Tome to draw ([35], §8).
 *
 * <h2>Derived, not authored</h2>
 *
 * <p>{@link NodeDef} carries no coordinates and deliberately still does not: a
 * hand-placed graph has to be re-authored every time a node is added, and the
 * node set is still moving (#102, #103). The grid falls out of two things the
 * definitions already state — the branch a node belongs to, and what it
 * requires — so a new node lands somewhere sensible with no layout edit at
 * all.</p>
 *
 * <ul>
 *   <li><b>Column</b> is the node's branch, in the order branches first appear
 *       in {@code NodeDefs}. That order is the one a reader of the spec sees.</li>
 *   <li><b>Row</b> orders a branch's nodes by prerequisite depth, so a node
 *       always sits below everything inside its own branch that it needs.</li>
 * </ul>
 *
 * <p>Depth is a whole-tree measure, not a per-branch one, so a node gated behind
 * another branch still sorts after its cheap neighbours. Rows are then assigned
 * by position within the branch rather than by depth directly, because several
 * nodes commonly share a depth — three root nodes in one branch would otherwise
 * be dealt the same cell.</p>
 *
 * <h2>No Minecraft here</h2>
 *
 * <p>Deliberately pure. Layout is the one part of a screen that is ordinary
 * logic with a right answer, so it lives where {@code src/test/java} can reach
 * it; nothing in this class may import a Minecraft type.</p>
 */
public final class TreeLayout {

    private TreeLayout() {
    }

    /** A node's grid position. {@code column} is its branch, {@code row} its rank within it. */
    public record Cell(String nodeId, String branch, int column, int row) {
    }

    /**
     * A whole tree's placement.
     *
     * @param branches branch labels, left to right
     * @param cells    node id to position, in the order the nodes were given
     */
    public record Layout(List<String> branches, Map<String, Cell> cells, int columnCount, int rowCount) {

        public Layout {
            branches = List.copyOf(branches);
            cells = Map.copyOf(cells);
        }

        /** The cell for a node, or {@code null} if it was not part of the laid-out set. */
        public Cell cell(String nodeId) {
            return cells.get(nodeId);
        }
    }

    /**
     * Lays out the given nodes.
     *
     * <p>The caller passes exactly the nodes it intends to draw — Ultimine nodes
     * are already filtered out when that mod is absent, for instance. A
     * prerequisite pointing outside the given set is treated as satisfied for
     * depth purposes rather than dragging an invisible node into the grid.</p>
     */
    public static Layout of(List<NodeDef> nodes) {
        Map<String, NodeDef> byId = new HashMap<>();
        for (NodeDef def : nodes) {
            byId.put(def.id(), def);
        }

        Map<String, Integer> depths = new HashMap<>();
        for (NodeDef def : nodes) {
            depthOf(def.id(), byId, depths, new HashSet<>());
        }

        // Declaration order decides both the branch columns and the tie-break
        // between nodes at equal depth, so the grid matches the reading order of
        // NodeDefs rather than hash order.
        Map<String, Integer> declarationOrder = new HashMap<>();
        Set<String> branchOrder = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            declarationOrder.put(nodes.get(i).id(), i);
            branchOrder.add(nodes.get(i).branch());
        }
        List<String> branches = new ArrayList<>(branchOrder);

        Map<String, List<NodeDef>> byBranch = new LinkedHashMap<>();
        for (String branch : branches) {
            byBranch.put(branch, new ArrayList<>());
        }
        for (NodeDef def : nodes) {
            byBranch.get(def.branch()).add(def);
        }

        Map<String, Cell> cells = new LinkedHashMap<>();
        int rowCount = 0;
        for (int column = 0; column < branches.size(); column++) {
            String branch = branches.get(column);
            List<NodeDef> inBranch = byBranch.get(branch);
            inBranch.sort(Comparator
                    .comparingInt((NodeDef def) -> depths.getOrDefault(def.id(), 0))
                    .thenComparingInt(def -> declarationOrder.get(def.id())));
            for (int row = 0; row < inBranch.size(); row++) {
                NodeDef def = inBranch.get(row);
                cells.put(def.id(), new Cell(def.id(), branch, column, row));
            }
            rowCount = Math.max(rowCount, inBranch.size());
        }

        return new Layout(branches, cells, branches.size(), rowCount);
    }

    /**
     * Longest prerequisite chain ending at {@code id}.
     *
     * <p>{@code visiting} makes a cycle finite rather than fatal. A cycle in the
     * definitions is a bug, but it is a bug that should show up as a strange
     * looking graph the next time someone opens the Tome, not as a stack
     * overflow that takes the client down with it.</p>
     */
    private static int depthOf(String id, Map<String, NodeDef> byId, Map<String, Integer> memo,
                               Set<String> visiting) {
        Integer known = memo.get(id);
        if (known != null) {
            return known;
        }
        NodeDef def = byId.get(id);
        if (def == null || !visiting.add(id)) {
            return 0;
        }
        int depth = 0;
        for (Prereq prereq : def.prereqs()) {
            depth = Math.max(depth, depthOf(prereq.nodeId(), byId, memo, visiting) + 1);
        }
        visiting.remove(id);
        memo.put(id, depth);
        return depth;
    }
}
