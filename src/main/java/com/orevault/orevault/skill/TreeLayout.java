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
import java.util.function.ToIntFunction;

import com.orevault.orevault.skill.NodeDef.Prereq;

/**
 * Places the Resonance tree for the Tome to draw (§6.1, §8, #136).
 *
 * <h2>What replaced the grid, and why</h2>
 *
 * <p>[35] shipped a column-per-cluster grid. It was rejected on playtest for
 * three reasons, and each one is a property this class now has to hold:</p>
 *
 * <ol>
 *   <li><b>Vertical position implied prerequisites that did not exist.</b>
 *       Gravel Purge sat directly under Common Ore Boost while both were
 *       available from the first skill point.</li>
 *   <li><b>Edges ran to node centres</b>, so a prerequisite line crossed the
 *       boxes in between and the text inside them.</li>
 *   <li><b>Boxes were a fixed width</b>, so longer names were truncated.</li>
 * </ol>
 *
 * <h2>Bands, lanes and gutters</h2>
 *
 * <p>The tree is a vertical stack of <b>bands</b>. Every box in a band shares a
 * top edge and a height, so the {@link #BAND_GAP} between two bands is a
 * horizontal strip containing nothing, all the way across. That is not a
 * cosmetic detail — it is what makes edge routing provable.</p>
 *
 * <p>Within a band, boxes sit in one of {@link #LANE_COUNT} <b>lanes</b>
 * staggered around a centre spine, filled from the middle outwards. Lane
 * x-positions are computed once for the whole tree from the widest box in each
 * lane, so a chain of prerequisites runs straight down a lane and unrelated
 * nodes fan out sideways instead of stacking.</p>
 *
 * <p>Left and right of the lanes are the <b>gutters</b>, {@link #GUTTER} wide,
 * which hold no boxes. A long edge leaves its source into the gap below it,
 * runs sideways along that gap into a gutter, down the gutter, and back in
 * along the gap above its target. Every segment of that route is inside a gap
 * or a gutter, so it cannot cross a box whatever the node set does later.</p>
 *
 * <h2>Derived, not authored</h2>
 *
 * <p>{@link NodeDef} carries no coordinates and deliberately still does not: a
 * hand-placed graph has to be re-authored every time a node is added, and the
 * node set moved by 30 nodes in one pass (#137). Everything here falls out of
 * what the definitions already state — cluster, node class, fork membership and
 * prerequisites — so a new node lands somewhere sensible with no layout edit.</p>
 *
 * <h2>No Minecraft here</h2>
 *
 * <p>Deliberately pure, so {@code src/test/java} can reach it; nothing in this
 * class may import a Minecraft type. Text measurement is the one thing layout
 * genuinely needs from the client, so it arrives as a {@code ToIntFunction}
 * the caller fills from {@code Font#width} and a test fills from a stand-in.</p>
 */
public final class TreeLayout {

    /** Uniform box height. Bands depend on every box in them being the same height. */
    public static final int NODE_HEIGHT = 28;
    /** Anchors are a single line of text, so they are shorter than a node. */
    public static final int ANCHOR_HEIGHT = 20;
    /** Horizontal padding either side of a node's name inside its box. */
    public static final int TEXT_PADDING = 6;
    public static final int MIN_NODE_WIDTH = 76;
    /**
     * Wide enough for the longest name in the tree.
     *
     * <p>That is "Volatile Veins: Ultimine Gambit", which §6.1 pins as a display
     * name rather than leaving to the renderer. A cap that does not fit it is a
     * cap that truncates a real node, which is one of the three things #136
     * exists to stop, so this number follows the names rather than the reverse.
     * {@code TreeLayoutTest} fails if a new node ever outgrows it.</p>
     */
    public static final int MAX_NODE_WIDTH = 172;
    /** Space between lanes, and the vertical space between bands. */
    public static final int LANE_GAP = 10;
    public static final int BAND_GAP = 16;
    /** Edge-routing corridor either side of the lanes. Holds no boxes, ever. */
    public static final int GUTTER = 22;

    /**
     * How many nodes can sit side by side in one band.
     *
     * <p>Three, because §6.1 asks for nodes staggered left and right of a centre
     * line — which is a centre lane and one either side, and not much else. It
     * is also the widest fork in the tree (Ore Attunement's three focus
     * options), so a fork fills a band exactly rather than needing room set
     * aside beside it.</p>
     *
     * <p>The trade is a tall tree rather than a wide one. That is the right way
     * round for a book: the clusters already run top to bottom, so the scroll
     * follows the reading order instead of fighting it, and a page 500 pixels
     * wide fits a normal GUI scale where 900 would not.</p>
     *
     * <p>A cluster with more nodes than this spills onto further bands rather
     * than growing sideways.</p>
     */
    public static final int LANE_COUNT = 3;

    /** Middle first, then alternating outwards — this is what makes a band read as staggered. */
    private static final int[] FILL_ORDER = {1, 0, 2};

    private TreeLayout() {
    }

    /** A placed node. Coordinates are tree space: origin top-left, y increasing downwards. */
    public record Box(String nodeId, Cluster cluster, NodeClass nodeClass, int band, int lane,
                      int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * A cluster heading.
     *
     * <p>Anchors are not nodes — they cannot be bought and they carry one number
     * each — so they live on {@link Cluster} rather than in {@code NodeDefs},
     * and they arrive here as their own record rather than as a {@code Box}. The
     * screen must not offer to purchase one.</p>
     */
    public record Anchor(Cluster cluster, int gate, int band, int x, int y, int width, int height) {

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < bottom();
        }
    }

    /** A corner in an edge route. */
    public record Point(int x, int y) {
    }

    /** A whole tree's placement, in tree space. */
    public record Layout(List<Anchor> anchors, Map<String, Box> boxes, int width, int height) {

        public Layout {
            anchors = List.copyOf(anchors);
            boxes = Map.copyOf(boxes);
        }

        /** The box for a node, or {@code null} if it was not part of the laid-out set. */
        public Box box(String nodeId) {
            return boxes.get(nodeId);
        }
    }

    // ----- placement -----

    /**
     * Lays out the given nodes.
     *
     * <p>The caller passes exactly the nodes it intends to draw — Ultimine nodes
     * are already filtered out when that mod is absent, for instance. A
     * prerequisite pointing outside the given set is treated as satisfied rather
     * than dragging an invisible node into the layout.</p>
     *
     * @param nameWidth rendered pixel width of a node's name, from the caller's font
     */
    public static Layout of(List<NodeDef> nodes, ToIntFunction<NodeDef> nameWidth) {
        if (nodes.isEmpty()) {
            return new Layout(List.of(), Map.of(), 0, 0);
        }

        Map<String, NodeDef> byId = new HashMap<>();
        for (NodeDef def : nodes) {
            byId.put(def.id(), def);
        }
        Map<String, Integer> widths = new HashMap<>();
        for (NodeDef def : nodes) {
            widths.put(def.id(), Math.clamp(
                    (long) nameWidth.applyAsInt(def) + TEXT_PADDING * 2, MIN_NODE_WIDTH, MAX_NODE_WIDTH));
        }

        Set<Cluster> clusterOrder = new LinkedHashSet<>();
        Map<String, Integer> declarationOrder = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            clusterOrder.add(nodes.get(i).cluster());
            declarationOrder.put(nodes.get(i).id(), i);
        }

        Map<String, Slot> slots = new LinkedHashMap<>();
        List<Cluster> anchorClusters = new ArrayList<>();
        Map<Cluster, Integer> anchorBands = new LinkedHashMap<>();

        int band = 0;
        for (Cluster cluster : clusterOrder) {
            List<NodeDef> inCluster = new ArrayList<>();
            for (NodeDef def : nodes) {
                if (def.cluster() == cluster) {
                    inCluster.add(def);
                }
            }
            anchorClusters.add(cluster);
            anchorBands.put(cluster, band);
            band = placeCluster(inCluster, byId, declarationOrder, band + 1, slots);
        }

        return assemble(anchorClusters, anchorBands, slots, byId, widths, band);
    }

    /** A node's band and lane, before either has a pixel position. */
    private record Slot(Cluster cluster, int band, int lane) {
    }

    /**
     * Places one cluster's nodes and returns the first free band after it.
     *
     * <p>Bands are finalised one at a time, and a node may only take a lane
     * whose occupant in the band above is a node it actually requires. Doing it
     * band by band rather than node by node is what makes that check sound: the
     * band above is complete and cannot gain a node later, which is exactly the
     * hole a node-at-a-time pass leaves.</p>
     *
     * <p>The visible consequence is that a cluster of ten independent nodes fills
     * five lanes, leaves the next band empty, and fills five more, rather than
     * stacking ten boxes into two rows that read as five chains of two.</p>
     */
    private static int placeCluster(List<NodeDef> inCluster, Map<String, NodeDef> byId,
                                    Map<String, Integer> declarationOrder, int firstBand,
                                    Map<String, Slot> slots) {
        Map<String, Integer> depths = new HashMap<>();
        for (NodeDef def : inCluster) {
            clusterDepth(def.id(), byId, def.cluster(), depths, new HashSet<>());
        }

        List<NodeDef> remaining = new ArrayList<>(inCluster);
        // Fork parents first at equal depth: they reserve a run of lanes for
        // their options and want the choice of where that run goes.
        remaining.sort(Comparator
                .comparingInt((NodeDef def) -> depths.getOrDefault(def.id(), 0))
                .thenComparingInt(def -> def.nodeClass() == NodeClass.FORK_PARENT ? 0 : 1)
                .thenComparingInt(def -> declarationOrder.getOrDefault(def.id(), 0)));
        remaining.removeIf(def -> def.nodeClass() == NodeClass.FORK_OPTION);

        Map<Integer, Map<Integer, String>> occupied = new HashMap<>();
        Map<Integer, Set<Integer>> reserved = new HashMap<>();
        int band = firstBand;
        int lastBand = firstBand;

        // A cluster whose nodes all wait on each other would spin here; the
        // bound turns that bug into a tall tree rather than a hung client.
        int guard = inCluster.size() * 2 + LANE_COUNT;
        while (!remaining.isEmpty() && guard-- > 0) {
            List<NodeDef> placedThisBand = new ArrayList<>();
            for (NodeDef def : remaining) {
                Integer lane = chooseLane(def, band, occupied, reserved, slots, byId);
                if (lane == null) {
                    continue;
                }
                occupied.computeIfAbsent(band, unused -> new HashMap<>()).put(lane, def.id());
                slots.put(def.id(), new Slot(def.cluster(), band, lane));
                placedThisBand.add(def);
                lastBand = Math.max(lastBand, band);

                if (def.nodeClass() == NodeClass.FORK_PARENT) {
                    lastBand = Math.max(lastBand,
                            placeForkOptions(def, band, lane, occupied, reserved, slots));
                }
            }
            remaining.removeAll(placedThisBand);
            band++;
        }

        // Anything the guard cut short still has to be drawn somewhere rather
        // than vanishing from the screen.
        for (NodeDef def : remaining) {
            int lane = slots.size() % LANE_COUNT;
            slots.put(def.id(), new Slot(def.cluster(), band, lane));
            lastBand = Math.max(lastBand, band);
            band++;
        }

        return lastBand + 1;
    }

    /**
     * The lane this node may take in this band, or {@code null} if it may not
     * take one here at all.
     *
     * <p>A node whose prerequisite sits in this same band has to wait for a
     * lower one, or it would be drawn beside the thing it requires.</p>
     */
    private static Integer chooseLane(NodeDef def, int band,
                                      Map<Integer, Map<Integer, String>> occupied,
                                      Map<Integer, Set<Integer>> reserved,
                                      Map<String, Slot> slots, Map<String, NodeDef> byId) {
        Integer parentLane = null;
        for (Prereq prereq : def.prereqs()) {
            Slot slot = slots.get(prereq.nodeId());
            NodeDef prereqDef = byId.get(prereq.nodeId());
            if (prereqDef == null || prereqDef.cluster() != def.cluster()) {
                continue; // drawn as an edge, not ordered
            }
            if (slot == null || slot.band() >= band) {
                return null;
            }
            parentLane = slot.lane();
        }

        Map<Integer, String> here = occupied.getOrDefault(band, Map.of());
        Set<Integer> blockedHere = reserved.getOrDefault(band, Set.of());
        Map<Integer, String> above = occupied.getOrDefault(band - 1, Map.of());

        List<Integer> candidates = new ArrayList<>();
        if (parentLane != null) {
            candidates.add(parentLane); // a chain reads best running straight down
        }
        for (int lane : FILL_ORDER) {
            if (!candidates.contains(lane)) {
                candidates.add(lane);
            }
        }

        for (int lane : candidates) {
            if (here.containsKey(lane) || blockedHere.contains(lane)) {
                continue;
            }
            String occupant = above.get(lane);
            if (occupant != null && !requires(def, occupant)) {
                continue; // would read as a prerequisite that does not exist
            }
            if (def.nodeClass() == NodeClass.FORK_PARENT && !forkRunFits(def, lane, here, blockedHere)) {
                continue;
            }
            return lane;
        }
        return null;
    }

    /** Whether a fork parent at this lane has room for its options' run of lanes beside it. */
    private static boolean forkRunFits(NodeDef parent, int lane, Map<Integer, String> here,
                                       Set<Integer> blockedHere) {
        int count = NodeDefs.forkOptions(parent.id()).size();
        if (count == 0) {
            return true;
        }
        int start = forkRunStart(lane, count);
        for (int l = start; l < start + count; l++) {
            if (l != lane && (here.containsKey(l) || blockedHere.contains(l))) {
                return false;
            }
        }
        return true;
    }

    private static int forkRunStart(int lane, int count) {
        return Math.clamp((long) lane - count / 2, 0, Math.max(0, LANE_COUNT - count));
    }

    /**
     * Places a fork's options side by side in the band below their parent.
     *
     * <p>The run of lanes is also reserved in the parent's own band. An option
     * is only legible as belonging to its parent if nothing unrelated is sitting
     * directly above it, and reserving the run is cheaper than discovering the
     * collision after the fact and shuffling.</p>
     */
    private static int placeForkOptions(NodeDef parent, int parentBand, int parentLane,
                                        Map<Integer, Map<Integer, String>> occupied,
                                        Map<Integer, Set<Integer>> reserved,
                                        Map<String, Slot> slots) {
        List<NodeDef> options = NodeDefs.forkOptions(parent.id());
        if (options.isEmpty()) {
            return parentBand;
        }
        int start = forkRunStart(parentLane, options.size());
        Set<Integer> reserveHere = reserved.computeIfAbsent(parentBand, unused -> new HashSet<>());
        Map<Integer, String> below = occupied.computeIfAbsent(parentBand + 1, unused -> new HashMap<>());

        for (int i = 0; i < options.size(); i++) {
            int lane = start + i;
            reserveHere.add(lane);
            below.put(lane, options.get(i).id());
            slots.put(options.get(i).id(), new Slot(parent.cluster(), parentBand + 1, lane));
        }
        return parentBand + 1;
    }

    /** Whether {@code def} really depends on {@code candidateId}, by prerequisite or by fork. */
    private static boolean requires(NodeDef def, String candidateId) {
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

    /**
     * Longest prerequisite chain ending at {@code id} within its own cluster.
     *
     * <p>{@code visiting} makes a cycle finite rather than fatal. A cycle in the
     * definitions is a bug, but it is a bug that should show up as a strange
     * looking graph the next time someone opens the Tome, not as a stack
     * overflow that takes the client down with it.</p>
     */
    private static int clusterDepth(String id, Map<String, NodeDef> byId, Cluster cluster,
                                    Map<String, Integer> memo, Set<String> visiting) {
        Integer known = memo.get(id);
        if (known != null) {
            return known;
        }
        NodeDef def = byId.get(id);
        if (def == null || def.cluster() != cluster || !visiting.add(id)) {
            return 0;
        }
        int depth = 0;
        for (Prereq prereq : def.prereqs()) {
            depth = Math.max(depth, clusterDepth(prereq.nodeId(), byId, cluster, memo, visiting) + 1);
        }
        if (def.forkParentId() != null) {
            depth = Math.max(depth, clusterDepth(def.forkParentId(), byId, cluster, memo, visiting) + 1);
        }
        visiting.remove(id);
        memo.put(id, depth);
        return depth;
    }

    // ----- pixel geometry -----

    /**
     * Turns bands and lanes into pixels.
     *
     * <p>Lane widths are computed once across the whole tree rather than per
     * band, so a prerequisite chain running down one lane stays vertically
     * aligned instead of wobbling with whatever else shares each band.</p>
     */
    private static Layout assemble(List<Cluster> anchorClusters, Map<Cluster, Integer> anchorBands,
                                   Map<String, Slot> slots, Map<String, NodeDef> byId,
                                   Map<String, Integer> widths, int bandCount) {
        int[] laneWidth = new int[LANE_COUNT];
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            laneWidth[lane] = MIN_NODE_WIDTH;
        }
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            int lane = entry.getValue().lane();
            laneWidth[lane] = Math.max(laneWidth[lane], widths.getOrDefault(entry.getKey(), MIN_NODE_WIDTH));
        }

        int[] laneX = new int[LANE_COUNT];
        int cursor = GUTTER;
        for (int lane = 0; lane < LANE_COUNT; lane++) {
            laneX[lane] = cursor;
            cursor += laneWidth[lane] + LANE_GAP;
        }
        int laneAreaWidth = cursor - LANE_GAP - GUTTER;
        int totalWidth = laneAreaWidth + GUTTER * 2;

        Set<Integer> anchorBandNumbers = new HashSet<>(anchorBands.values());
        int[] bandY = new int[bandCount + 1];
        int y = 0;
        for (int b = 0; b < bandCount; b++) {
            bandY[b] = y;
            y += (anchorBandNumbers.contains(b) ? ANCHOR_HEIGHT : NODE_HEIGHT) + BAND_GAP;
        }
        bandY[bandCount] = y;
        int totalHeight = Math.max(0, y - BAND_GAP);

        Map<String, Box> boxes = new LinkedHashMap<>();
        for (Map.Entry<String, Slot> entry : slots.entrySet()) {
            Slot slot = entry.getValue();
            NodeDef def = byId.get(entry.getKey());
            int width = widths.getOrDefault(entry.getKey(), MIN_NODE_WIDTH);
            // Centred in its lane, so a narrow node under a wide one still lines up.
            int x = laneX[slot.lane()] + (laneWidth[slot.lane()] - width) / 2;
            boxes.put(entry.getKey(), new Box(entry.getKey(), slot.cluster(),
                    def == null ? NodeClass.SMALL : def.nodeClass(),
                    slot.band(), slot.lane(), x, bandY[slot.band()], width, NODE_HEIGHT));
        }

        List<Anchor> anchors = new ArrayList<>();
        for (Cluster cluster : anchorClusters) {
            int b = anchorBands.get(cluster);
            anchors.add(new Anchor(cluster, NodeDefs.anchorGate(cluster), b,
                    GUTTER, bandY[b], laneAreaWidth, ANCHOR_HEIGHT));
        }

        return new Layout(anchors, boxes, totalWidth, totalHeight);
    }

    // ----- edge routing -----

    /**
     * The polyline for a prerequisite edge, from a border of {@code from} to a
     * border of {@code to}.
     *
     * <p>Two cases. Boxes one band apart get a short elbow through the gap
     * between them. Anything further apart is routed out into a gutter, because
     * a straight line between distant boxes is exactly what crossed the node
     * text in the grid it replaces.</p>
     *
     * <p>Every segment lies inside a band gap or a gutter, both of which are
     * empty by construction, so no route can cross a box. That is a property of
     * the geometry rather than of the current node set, which is why it survives
     * nodes being added.</p>
     *
     * <p>{@code from} is the prerequisite and may sit <em>below</em> its
     * dependent — Ancient Traces in Assay requires Vault Expansion in Mastery,
     * the last cluster — so the route is mirrored rather than assuming the
     * prerequisite is always higher up.</p>
     */
    public static List<Point> edge(Layout layout, Box from, Box to) {
        boolean downward = to.band() > from.band();
        int fromY = downward ? from.bottom() : from.y();
        int toY = downward ? to.y() : to.bottom();
        int fromGap = downward ? fromY + BAND_GAP / 2 : fromY - BAND_GAP / 2;
        int toGap = downward ? toY - BAND_GAP / 2 : toY + BAND_GAP / 2;

        if (Math.abs(to.band() - from.band()) == 1) {
            return List.of(
                    new Point(from.centerX(), fromY),
                    new Point(from.centerX(), fromGap),
                    new Point(to.centerX(), fromGap),
                    new Point(to.centerX(), toY));
        }

        int gutterX = from.centerX() <= layout.width() / 2 ? GUTTER / 2 : layout.width() - GUTTER / 2;
        return List.of(
                new Point(from.centerX(), fromY),
                new Point(from.centerX(), fromGap),
                new Point(gutterX, fromGap),
                new Point(gutterX, toGap),
                new Point(to.centerX(), toGap),
                new Point(to.centerX(), toY));
    }
}
