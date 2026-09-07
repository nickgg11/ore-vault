package com.orevault.orevault.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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
 * Places the Resonance tree for the Tome to draw (§6.1, §8, #136, #147).
 *
 * <h2>Hubs, rings and sectors</h2>
 *
 * <p>Each cluster is a <b>hub</b>: its anchor sits at the centre and the
 * cluster's nodes are placed on concentric <b>rings</b> around it, so a node's
 * distance from its hub is its prerequisite depth inside that cluster and
 * nothing else. Hubs are strung down a vertical <b>spine</b> in cluster order.
 * That is the Diablo 4 reference §8 asks for — a spine you follow downward with
 * clusters radiating off it — rather than the list the bands produced.</p>
 *
 * <p>Nodes are never placed straight above or below their hub. A
 * {@link #SPINE_HALF_DEG} wedge either side of vertical is left empty at every
 * radius, which gives each cluster a clear <b>corridor</b> running up and down
 * through it. The spine is drawn in that corridor, and so is every edge that has
 * to leave a cluster. What remains is two <b>sectors</b>, one left and one right,
 * and the cluster's nodes fan out into them.</p>
 *
 * <h2>Why the geometry is what proves the drawing</h2>
 *
 * <p>Rings are sized so that two things hold, and both are asserted rather than
 * hoped for:</p>
 *
 * <ol>
 *   <li>The <b>annuli are disjoint.</b> Every box on a ring lies between that
 *       ring's inner and outer radius, and the next ring starts at least
 *       {@link #RING_GAP} further out. The strip between two rings therefore
 *       contains no box at any angle.</li>
 *   <li>The <b>angular footprints on a ring are disjoint.</b> Seen from the hub,
 *       no two boxes on the same ring overlap in angle, with a few degrees to
 *       spare. A ray leaving the hub at a node's own angle therefore meets that
 *       node and no other.</li>
 * </ol>
 *
 * <p>Every edge route is built from three moves, each of which lands in one of
 * those empty places: radially along a node's own angle, around an arc inside a
 * gap between rings, or up and down the vertical corridor. No route can cross a
 * box, and that stays true when nodes are added — the ring simply grows until its
 * contents fit, which is the only thing {@link #fitRing} does.</p>
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
 * genuinely needs from the client, so it arrives as a {@code ToIntFunction} the
 * caller fills from {@code Font#width} and a test fills from a stand-in.</p>
 */
public final class TreeLayout {

    /** Uniform box height: two lines of text and the padding around them. */
    public static final int NODE_HEIGHT = 28;
    /** A hub carries its cluster name and its points-spent gate on two lines. */
    public static final int ANCHOR_HEIGHT = 32;
    /** Horizontal padding either side of a node's text inside its box. */
    public static final int TEXT_PADDING = 6;
    public static final int MIN_NODE_WIDTH = 80;

    /**
     * Wide enough for the widest line any node box has to draw.
     *
     * <p>Not the widest <em>name</em>: the second line carries the tier, the cost
     * and the level requirement, and a fork parent's second line carries the
     * display name of whichever option is active. Measuring only the name is what
     * let text run out of the box, so the caller now measures every line a node
     * can ever show and this caps the answer. {@code TreeLayoutTest} fails if a
     * real node ever outgrows it.</p>
     */
    public static final int MAX_NODE_WIDTH = 208;

    public static final int MIN_ANCHOR_WIDTH = 112;
    public static final int MAX_ANCHOR_WIDTH = 240;

    /** Empty strip between one ring's outer edge and the next ring's inner edge. */
    public static final int RING_GAP = 22;
    /** Empty strip between one cluster's bounding circle and the next. */
    public static final int CLUSTER_GAP = 48;
    /** Edge-routing corridor outside every cluster, left and right. Holds no boxes, ever. */
    public static final int GUTTER = 30;

    /**
     * Half-width, in degrees, of the empty wedge above and below every hub.
     *
     * <p>This is the corridor. It is what lets the spine run from one hub to the
     * next through the middle of a cluster, and what gives an edge leaving a
     * cluster somewhere to go that is guaranteed free at every radius.</p>
     */
    public static final int SPINE_HALF_DEG = 18;

    /** Smallest angular clearance between two boxes on the same ring, in degrees. */
    private static final double ANGULAR_GAP_DEG = 3.0;

    /** How far a ring grows when its contents do not fit, and how often it may try. */
    private static final int RING_GROWTH_STEP = 8;
    private static final int RING_GROWTH_TRIES = 600;

    /** Passes used to settle a ring's angles; a box's angular size depends on its angle. */
    private static final int ANGLE_PASSES = 6;

    /** Half the angular span available to one sector, in radians. */
    private static final double SECTOR_HALF = Math.toRadians(90 - SPINE_HALF_DEG);

    private TreeLayout() {
    }

    // ----- public shapes -----

    /**
     * A placed node.
     *
     * <p>Coordinates are tree space: origin top-left, y increasing downwards.
     * {@code ring} is the node's prerequisite depth inside its cluster, counting
     * the hub as ring 0, and {@code angle} is measured from the hub in radians
     * with 0 due right and positive turning upwards.</p>
     */
    public record Box(String nodeId, Cluster cluster, NodeClass nodeClass, int ring, double angle,
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

        public int centerY() {
            return y + height / 2;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * A cluster's hub.
     *
     * <p>Anchors are not nodes — they cannot be bought and they carry one number
     * each — so they live on {@link Cluster} rather than in {@code NodeDefs}, and
     * they arrive here as their own record rather than as a {@code Box}. The
     * screen must not offer to purchase one.</p>
     */
    public record Anchor(Cluster cluster, int gate, int x, int y, int width, int height) {

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }

        public boolean contains(int px, int py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /** A corner in an edge route. */
    public record Point(int x, int y) {
    }

    /**
     * One ring's radial extent.
     *
     * <p>{@code inner} and {@code outer} bound every box on the ring — not the
     * ring's nominal radius, but the nearest and furthest any part of a box gets
     * to the hub. That is what makes the strip between two rings provably
     * empty.</p>
     */
    public record Ring(int index, double radius, double inner, double outer) {
    }

    /** Where one cluster sits and how its rings are spaced. */
    public record ClusterGeometry(Cluster cluster, int centerX, int centerY, int hubWidth, int hubHeight,
                                  List<Ring> rings, double outerRadius) {

        public ClusterGeometry {
            rings = List.copyOf(rings);
        }

        public Ring ring(int index) {
            return rings.get(Math.clamp(index, 0, rings.size() - 1));
        }

        /**
         * Radius of the empty strip just outside {@code ring}.
         *
         * <p>Beyond the last ring there is no next ring to meet, so the strip sits
         * half a {@link #RING_GAP} past the outermost box — still outside
         * everything, which is all a route needs.</p>
         */
        public double gapOutside(int ring) {
            int index = Math.clamp(ring, 0, rings.size() - 1);
            if (index + 1 < rings.size()) {
                return (rings.get(index).outer() + rings.get(index + 1).inner()) / 2.0;
            }
            return rings.get(index).outer() + RING_GAP / 2.0;
        }

        /** Radius of the empty strip just inside {@code ring}. Ring 0 is the hub itself. */
        public double gapInside(int ring) {
            return gapOutside(Math.max(0, ring - 1));
        }
    }

    /** A whole tree's placement, in tree space. */
    public record Layout(List<Anchor> anchors, Map<String, Box> boxes,
                         Map<Cluster, ClusterGeometry> clusters, int width, int height) {

        public Layout {
            anchors = List.copyOf(anchors);
            boxes = Map.copyOf(boxes);
            clusters = Map.copyOf(clusters);
        }

        /** The box for a node, or {@code null} if it was not part of the laid-out set. */
        public Box box(String nodeId) {
            return boxes.get(nodeId);
        }

        public ClusterGeometry geometry(Cluster cluster) {
            return clusters.get(cluster);
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
     * @param contentWidth widest line the caller will draw inside a node's box,
     *                     in pixels, measured from its own font
     * @param anchorWidth  width the caller needs for a cluster's heading
     */
    public static Layout of(List<NodeDef> nodes, ToIntFunction<NodeDef> contentWidth,
                            ToIntFunction<Cluster> anchorWidth) {
        if (nodes.isEmpty()) {
            return new Layout(List.of(), Map.of(), Map.of(), 0, 0);
        }

        Map<String, NodeDef> byId = new HashMap<>();
        Map<String, Integer> declarationOrder = new HashMap<>();
        Set<Cluster> clusterOrder = new LinkedHashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            NodeDef def = nodes.get(i);
            byId.put(def.id(), def);
            declarationOrder.put(def.id(), i);
            clusterOrder.add(def.cluster());
        }

        Map<String, Integer> widths = new HashMap<>();
        for (NodeDef def : nodes) {
            widths.put(def.id(), Math.clamp(
                    (long) contentWidth.applyAsInt(def) + TEXT_PADDING * 2,
                    MIN_NODE_WIDTH, MAX_NODE_WIDTH));
        }

        Map<Cluster, List<Polar>> placed = new EnumMap<>(Cluster.class);
        Map<Cluster, List<Ring>> ringSets = new EnumMap<>(Cluster.class);
        Map<Cluster, Integer> anchorWidths = new EnumMap<>(Cluster.class);

        for (Cluster cluster : clusterOrder) {
            int hubWidth = Math.clamp((long) anchorWidth.applyAsInt(cluster) + TEXT_PADDING * 2,
                    MIN_ANCHOR_WIDTH, MAX_ANCHOR_WIDTH);
            anchorWidths.put(cluster, hubWidth);

            List<NodeDef> inCluster = new ArrayList<>();
            for (NodeDef def : nodes) {
                if (def.cluster() == cluster) {
                    inCluster.add(def);
                }
            }
            List<Ring> rings = new ArrayList<>();
            placed.put(cluster,
                    placeCluster(inCluster, byId, declarationOrder, widths, hubWidth, rings));
            ringSets.put(cluster, rings);
        }

        return assemble(clusterOrder, placed, ringSets, anchorWidths, widths);
    }

    /**
     * Convenience for callers with nothing but a name to measure.
     *
     * <p>Used by tests. The client passes its own anchor measurement, because a
     * cluster heading carries its points-spent gate beside the name and the two
     * together are what has to fit.</p>
     */
    public static Layout of(List<NodeDef> nodes, ToIntFunction<NodeDef> contentWidth) {
        return of(nodes, contentWidth, cluster -> cluster.displayName().length() * 6);
    }

    /**
     * A node's position around its hub, before the hub has a position of its own.
     *
     * <p>{@code side} is -1 for the left sector and +1 for the right, and exists
     * so that a chain of prerequisites stays on one side of the spine instead of
     * being flung across it every time it gains a ring.</p>
     */
    private record Polar(NodeDef def, int ring, int side, double angle, int width, int height) {

        Polar withAngle(double newAngle) {
            return new Polar(def, ring, side, newAngle, width, height);
        }
    }

    /**
     * Places one cluster's nodes around its hub and records the rings it used.
     *
     * <p>A node's ring is its prerequisite depth inside its own cluster, so
     * radial distance from the hub means exactly one thing. That is the property
     * the band layout could not hold as geometry: there, two unrelated nodes
     * could end up one directly above the other and read as a chain, and keeping
     * them apart took a placement rule rather than falling out of the shape.</p>
     */
    private static List<Polar> placeCluster(List<NodeDef> inCluster, Map<String, NodeDef> byId,
                                            Map<String, Integer> declarationOrder,
                                            Map<String, Integer> widths, int hubWidth,
                                            List<Ring> rings) {
        rings.add(hubRing(hubWidth));
        if (inCluster.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> depths = new HashMap<>();
        for (NodeDef def : inCluster) {
            clusterDepth(def.id(), byId, def.cluster(), depths, new HashSet<>());
        }

        int maxDepth = 0;
        for (NodeDef def : inCluster) {
            maxDepth = Math.max(maxDepth, depths.getOrDefault(def.id(), 0));
        }

        Map<String, Integer> sides = new HashMap<>();
        Map<String, Double> angles = new HashMap<>();
        List<Polar> out = new ArrayList<>();

        for (int depth = 0; depth <= maxDepth; depth++) {
            List<NodeDef> onRing = new ArrayList<>();
            for (NodeDef def : inCluster) {
                if (depths.getOrDefault(def.id(), 0) == depth) {
                    onRing.add(def);
                }
            }
            if (onRing.isEmpty()) {
                double outer = rings.getLast().outer();
                rings.add(new Ring(depth + 1, outer, outer, outer));
                continue;
            }
            onRing.sort(Comparator.comparingInt(def -> declarationOrder.getOrDefault(def.id(), 0)));

            List<Polar> right = new ArrayList<>();
            List<Polar> left = new ArrayList<>();
            int alternate = 0;
            for (NodeDef def : onRing) {
                Integer side = sides.get(sideSource(def, byId, depths));
                if (side == null) {
                    side = (alternate++ % 2 == 0) ? 1 : -1;
                }
                sides.put(def.id(), side);
                Polar polar = new Polar(def, depth + 1, side, 0,
                        widths.getOrDefault(def.id(), MIN_NODE_WIDTH), NODE_HEIGHT);
                (side > 0 ? right : left).add(polar);
            }
            // Ordering a deeper ring by where each node's parent ended up is what
            // stops two sibling groups interleaving and dragging their edges across
            // each other. At depth 0 there is no parent, so declaration order stands.
            if (depth > 0) {
                Comparator<Polar> byParentAngle = Comparator.comparingDouble(
                        polar -> angles.getOrDefault(sideSource(polar.def(), byId, depths), 0.0));
                right.sort(byParentAngle);
                left.sort(byParentAngle.reversed());
            }

            Ring fitted = fitRing(depth + 1, right, left, rings.getLast().outer());
            rings.add(fitted);
            for (Polar polar : right) {
                angles.put(polar.def().id(), polar.angle());
                out.add(polar);
            }
            for (Polar polar : left) {
                angles.put(polar.def().id(), polar.angle());
                out.add(polar);
            }
        }
        return out;
    }

    /** Ring 0: the hub box itself, bounded by its own corners. */
    private static Ring hubRing(int hubWidth) {
        double outer = Math.hypot(hubWidth / 2.0, ANCHOR_HEIGHT / 2.0);
        return new Ring(0, 0, 0, outer);
    }

    /**
     * The node this one takes its side of the spine from.
     *
     * <p>The deepest in-cluster prerequisite, because that is the one that set
     * this node's ring. Following it keeps a chain running outward on one side
     * rather than crossing the corridor at every step.</p>
     */
    private static String sideSource(NodeDef def, Map<String, NodeDef> byId,
                                     Map<String, Integer> depths) {
        String best = null;
        int bestDepth = -1;
        if (def.forkParentId() != null) {
            best = def.forkParentId();
            bestDepth = depths.getOrDefault(best, 0);
        }
        for (Prereq prereq : def.prereqs()) {
            NodeDef other = byId.get(prereq.nodeId());
            if (other == null || other.cluster() != def.cluster()) {
                continue;
            }
            int depth = depths.getOrDefault(prereq.nodeId(), 0);
            if (depth > bestDepth) {
                best = prereq.nodeId();
                bestDepth = depth;
            }
        }
        return best == null ? "" : best;
    }

    /**
     * Grows a ring until everything on it fits, and returns the band it occupies.
     *
     * <p>The two conditions are the ones the routing depends on: every box clears
     * the ring inside it by {@link #RING_GAP}, and no two boxes on this ring
     * overlap in angle as seen from the hub. A larger radius always helps with
     * both — angular size falls away as the radius rises — so the loop
     * terminates, and the cost of a crowded ring is a wider cluster rather than a
     * collision.</p>
     */
    private static Ring fitRing(int index, List<Polar> right, List<Polar> left, double previousOuter) {
        double radius = previousOuter + RING_GAP + NODE_HEIGHT / 2.0 + 1;

        for (int attempt = 0; attempt < RING_GROWTH_TRIES; attempt++) {
            if (spreadSide(right, radius, 0) && spreadSide(left, radius, Math.PI)) {
                double inner = Double.MAX_VALUE;
                double outer = 0;
                for (Polar polar : concat(right, left)) {
                    inner = Math.min(inner, radialExtent(polar, radius, true));
                    outer = Math.max(outer, radialExtent(polar, radius, false));
                }
                if (inner >= previousOuter + RING_GAP) {
                    return new Ring(index, radius, inner, outer);
                }
            }
            radius += RING_GROWTH_STEP;
        }
        // Unreachable for any node set that fits in memory. A ring that somehow
        // never settles is still drawn rather than dropped on the floor.
        return new Ring(index, radius, radius, radius);
    }

    /**
     * Assigns angles to one side of one ring, in place, or reports that they do
     * not fit at this radius.
     *
     * <p>A box's angular size depends on the angle it is placed at — a wide, flat
     * box seen end-on from the hub subtends far less than the same box seen
     * broadside — so the angles and the sizes have to settle together. Repeating
     * the sweep a handful of times does that: each pass measures the boxes where
     * the last pass put them.</p>
     */
    private static boolean spreadSide(List<Polar> side, double radius, double sectorCenter) {
        if (side.isEmpty()) {
            return true;
        }
        double gap = Math.toRadians(ANGULAR_GAP_DEG);
        double[] half = new double[side.size()];
        for (int i = 0; i < half.length; i++) {
            half[i] = Math.atan2(side.get(i).width() / 2.0, Math.max(1, radius));
        }

        for (int pass = 0; pass < ANGLE_PASSES; pass++) {
            double total = gap * (side.size() - 1);
            for (double h : half) {
                total += 2 * h;
            }
            if (total > SECTOR_HALF * 2) {
                return false;
            }
            double cursor = sectorCenter - total / 2;
            for (int i = 0; i < side.size(); i++) {
                side.set(i, side.get(i).withAngle(cursor + half[i]));
                cursor += 2 * half[i] + gap;
                half[i] = angularHalfWidth(side.get(i), radius);
            }
        }

        // Checked against where the boxes actually ended up, not against the
        // estimate that put them there.
        for (int i = 0; i < side.size(); i++) {
            Polar polar = side.get(i);
            double h = angularHalfWidth(polar, radius);
            if (Math.abs(deviation(polar.angle() + h, sectorCenter)) > SECTOR_HALF
                    || Math.abs(deviation(polar.angle() - h, sectorCenter)) > SECTOR_HALF) {
                return false;
            }
            if (i > 0) {
                Polar previous = side.get(i - 1);
                if (Math.abs(deviation(polar.angle(), previous.angle()))
                        < h + angularHalfWidth(previous, radius) + gap) {
                    return false;
                }
            }
        }
        return true;
    }

    /** How wide a box looks from the hub: the half-angle its corners subtend. */
    private static double angularHalfWidth(Polar polar, double radius) {
        double cx = radius * Math.cos(polar.angle());
        double cy = radius * Math.sin(polar.angle());
        double hw = polar.width() / 2.0;
        double hh = polar.height() / 2.0;
        double worst = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                worst = Math.max(worst,
                        Math.abs(deviation(Math.atan2(cy + sy * hh, cx + sx * hw), polar.angle())));
            }
        }
        return worst;
    }

    /**
     * The nearest or furthest a box gets to its hub.
     *
     * <p>The nearest point of a rectangle is not always a corner — it is a point
     * on an edge whenever the hub falls within the box's own x or y span — so the
     * near case clamps rather than enumerating corners. Getting that wrong would
     * overstate a ring's inner radius and let the strip between two rings hold a
     * box after all, which is the one thing the routing must not discover at
     * runtime.</p>
     */
    private static double radialExtent(Polar polar, double radius, boolean nearest) {
        double cx = radius * Math.cos(polar.angle());
        double cy = radius * Math.sin(polar.angle());
        double hw = polar.width() / 2.0;
        double hh = polar.height() / 2.0;
        if (nearest) {
            return Math.hypot(Math.max(0, Math.abs(cx) - hw), Math.max(0, Math.abs(cy) - hh));
        }
        return Math.hypot(Math.abs(cx) + hw, Math.abs(cy) + hh);
    }

    /** Signed difference between two angles, in radians, always in (-pi, pi]. */
    private static double deviation(double angle, double reference) {
        return Math.atan2(Math.sin(angle - reference), Math.cos(angle - reference));
    }

    private static List<Polar> concat(List<Polar> a, List<Polar> b) {
        List<Polar> all = new ArrayList<>(a.size() + b.size());
        all.addAll(a);
        all.addAll(b);
        return all;
    }

    /**
     * Longest prerequisite chain ending at {@code id} within its own cluster.
     *
     * <p>A prerequisite in <em>another</em> cluster adds nothing. It is drawn as
     * an edge between two hubs and gated by the anchor, so counting it would push
     * a node out to the second ring with nothing at all on the ring inside it —
     * a node visibly held back by something that is not there. Vein
     * Proliferation is the case: it opens Excavation and requires Vein Expansion
     * over in Prospecting.</p>
     *
     * <p>{@code visiting} makes a cycle finite rather than fatal. A cycle in the
     * definitions is a bug, but it is a bug that should show up as a strange
     * looking graph the next time someone opens the Tome, not as a stack overflow
     * that takes the client down with it.</p>
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
            if (sameCluster(byId, prereq.nodeId(), cluster)) {
                depth = Math.max(depth, clusterDepth(prereq.nodeId(), byId, cluster, memo, visiting) + 1);
            }
        }
        if (sameCluster(byId, def.forkParentId(), cluster)) {
            depth = Math.max(depth, clusterDepth(def.forkParentId(), byId, cluster, memo, visiting) + 1);
        }
        visiting.remove(id);
        memo.put(id, depth);
        return depth;
    }

    private static boolean sameCluster(Map<String, NodeDef> byId, String id, Cluster cluster) {
        NodeDef def = id == null ? null : byId.get(id);
        return def != null && def.cluster() == cluster;
    }

    // ----- pixel geometry -----

    /**
     * Strings the hubs down the spine and turns polar positions into pixels.
     *
     * <p>Consecutive clusters clear each other by {@link #CLUSTER_GAP} measured
     * between their bounding circles, so the strip between two clusters is empty
     * all the way across — the same guarantee the gap between two rings gives, one
     * level up, and what lets an edge leaving one cluster for another travel
     * sideways without hitting anything.</p>
     */
    private static Layout assemble(Set<Cluster> clusterOrder, Map<Cluster, List<Polar>> placed,
                                   Map<Cluster, List<Ring>> ringSets,
                                   Map<Cluster, Integer> anchorWidths, Map<String, Integer> widths) {
        Map<Cluster, Double> radii = new EnumMap<>(Cluster.class);
        for (Cluster cluster : clusterOrder) {
            radii.put(cluster, ringSets.get(cluster).getLast().outer());
        }

        // Laid out around x = 0 and normalised at the end. Deriving the page size
        // from the radii instead leaves the widest box half a pixel into a gutter
        // on some roundings, and the gutters have to be genuinely empty for the
        // long edges routed down them to be clear.
        Map<Cluster, Integer> centerY = new EnumMap<>(Cluster.class);
        double y = 0;
        Cluster previous = null;
        for (Cluster cluster : clusterOrder) {
            y = previous == null ? 0 : y + radii.get(previous) + CLUSTER_GAP + radii.get(cluster);
            centerY.put(cluster, (int) Math.round(y));
            previous = cluster;
        }

        Map<String, Box> raw = new LinkedHashMap<>();
        List<Anchor> rawAnchors = new ArrayList<>();
        for (Cluster cluster : clusterOrder) {
            int cy = centerY.get(cluster);
            int hubWidth = anchorWidths.get(cluster);
            rawAnchors.add(new Anchor(cluster, NodeDefs.anchorGate(cluster),
                    -hubWidth / 2, cy - ANCHOR_HEIGHT / 2, hubWidth, ANCHOR_HEIGHT));

            List<Ring> rings = ringSets.get(cluster);
            for (Polar polar : placed.get(cluster)) {
                double radius = rings.get(polar.ring()).radius();
                int width = widths.getOrDefault(polar.def().id(), MIN_NODE_WIDTH);
                int px = (int) Math.round(radius * Math.cos(polar.angle())) - width / 2;
                int py = (int) Math.round(cy - radius * Math.sin(polar.angle())) - NODE_HEIGHT / 2;
                raw.put(polar.def().id(), new Box(polar.def().id(), cluster, polar.def().nodeClass(),
                        polar.ring(), polar.angle(), px, py, width, NODE_HEIGHT));
            }
        }

        int minX = 0;
        int maxX = 0;
        int minY = 0;
        int maxY = 0;
        for (Anchor anchor : rawAnchors) {
            minX = Math.min(minX, anchor.x());
            maxX = Math.max(maxX, anchor.right());
            minY = Math.min(minY, anchor.y());
            maxY = Math.max(maxY, anchor.bottom());
        }
        for (Box box : raw.values()) {
            minX = Math.min(minX, box.x());
            maxX = Math.max(maxX, box.right());
            minY = Math.min(minY, box.y());
            maxY = Math.max(maxY, box.bottom());
        }

        // Half a cluster gap top and bottom, because an edge leaving the first or
        // last cluster steps into exactly that strip on its way to a gutter.
        int shiftX = GUTTER - minX;
        int shiftY = CLUSTER_GAP / 2 - minY;
        int totalWidth = (maxX - minX) + GUTTER * 2;
        int totalHeight = (maxY - minY) + CLUSTER_GAP;

        Map<String, Box> boxes = new LinkedHashMap<>();
        for (Box box : raw.values()) {
            boxes.put(box.nodeId(), new Box(box.nodeId(), box.cluster(), box.nodeClass(), box.ring(),
                    box.angle(), box.x() + shiftX, box.y() + shiftY, box.width(), box.height()));
        }
        List<Anchor> anchors = new ArrayList<>();
        Map<Cluster, ClusterGeometry> geometry = new EnumMap<>(Cluster.class);
        for (Anchor anchor : rawAnchors) {
            anchors.add(new Anchor(anchor.cluster(), anchor.gate(), anchor.x() + shiftX,
                    anchor.y() + shiftY, anchor.width(), anchor.height()));
            geometry.put(anchor.cluster(), new ClusterGeometry(anchor.cluster(), shiftX,
                    centerY.get(anchor.cluster()) + shiftY, anchor.width(), ANCHOR_HEIGHT,
                    ringSets.get(anchor.cluster()), radii.get(anchor.cluster())));
        }

        return new Layout(anchors, boxes, geometry, totalWidth, totalHeight);
    }

    // ----- edge routing -----

    /**
     * The spine segment joining two hubs.
     *
     * <p>Straight down the corridor, which is the one place in a cluster
     * guaranteed to hold no box at any radius. It is what makes the run of
     * clusters read as an order rather than as a pile.</p>
     */
    public static List<Point> spine(Layout layout, Cluster from, Cluster to) {
        ClusterGeometry a = layout.geometry(from);
        ClusterGeometry b = layout.geometry(to);
        if (a == null || b == null) {
            return List.of();
        }
        return List.of(new Point(a.centerX(), a.centerY()), new Point(b.centerX(), b.centerY()));
    }

    /**
     * The line from a hub to a node on its first ring.
     *
     * <p>A node with no prerequisite inside its own cluster has nothing to hang
     * off but the anchor, and the anchor really is what gates it. Drawing the
     * spoke is what makes a cluster read as radiating from its centre rather than
     * as a ring of boxes that happen to surround one.</p>
     *
     * <p>Straight along the node's own ray, which meets that node and no other,
     * and stopping on both borders rather than at either centre.</p>
     */
    public static List<Point> spoke(Layout layout, Box to) {
        ClusterGeometry hub = layout.geometry(to.cluster());
        if (hub == null || to.ring() != 1) {
            return List.of();
        }
        return List.of(
                borderPoint(hub.centerX(), hub.centerY(), hub.hubWidth(), hub.hubHeight(),
                        to.angle(), true),
                borderPoint(to, false));
    }

    /**
     * The polyline for a prerequisite edge, from a border of {@code from} to a
     * border of {@code to}.
     *
     * <p>Three moves and nothing else: radially along a node's own angle, around
     * an arc inside the gap between two rings, and up or down the vertical
     * corridor. Each of those places is empty by construction — the ray at a
     * node's angle meets only that node, the gap between rings holds no box at
     * any angle, and the corridor is clear at every radius — so no route can cross
     * a box. The property belongs to the geometry rather than to today's node
     * set, which is why it survives nodes being added.</p>
     *
     * <p>{@code from} is the prerequisite and may sit further in, further out, or
     * in another cluster entirely — Ancient Traces in Assay requires Vault
     * Expansion in Mastery, the last cluster — so nothing here assumes the
     * prerequisite is the nearer of the two.</p>
     */
    public static List<Point> edge(Layout layout, Box from, Box to) {
        ClusterGeometry a = layout.geometry(from.cluster());
        ClusterGeometry b = layout.geometry(to.cluster());
        if (a == null || b == null) {
            return List.of(new Point(from.centerX(), from.centerY()),
                    new Point(to.centerX(), to.centerY()));
        }
        return from.cluster() == to.cluster()
                ? withinCluster(a, from, to)
                : betweenClusters(layout, a, b, from, to);
    }

    /**
     * An edge between two rings of the same hub.
     *
     * <p>Neighbouring rings share a gap, so the route is a short radial hop out,
     * an arc round to the target's angle, and a hop back in — which is what makes
     * a cluster read as radiating from its anchor. Rings further apart share no
     * gap, so the arc is replaced by a run along the corridor, the same move an
     * edge leaving the cluster makes.</p>
     */
    private static List<Point> withinCluster(ClusterGeometry hub, Box from, Box to) {
        boolean outward = to.ring() > from.ring();
        double leaveRadius = outward ? hub.gapOutside(from.ring()) : hub.gapInside(from.ring());
        double arriveRadius = outward ? hub.gapInside(to.ring()) : hub.gapOutside(to.ring());

        List<Point> route = new ArrayList<>();
        route.add(borderPoint(from, outward));
        route.add(polar(hub, from.angle(), leaveRadius));
        if (Math.abs(to.ring() - from.ring()) == 1) {
            addArc(route, hub, from.angle(), to.angle(), leaveRadius);
        } else {
            double corridor = outward ? -Math.PI / 2 : Math.PI / 2;
            addArc(route, hub, from.angle(), corridor, leaveRadius);
            route.add(polar(hub, corridor, arriveRadius));
            addArc(route, hub, corridor, to.angle(), arriveRadius);
        }
        route.add(polar(hub, to.angle(), arriveRadius));
        route.add(borderPoint(to, !outward));
        return route;
    }

    /**
     * An edge between two clusters.
     *
     * <p>It leaves through the corridor, crosses the empty strip between the two
     * clusters, runs along a gutter and comes back the same way. Long, and
     * deliberately so: a straight line between two nodes several clusters apart is
     * exactly what used to run through the text of everything in between.</p>
     */
    private static List<Point> betweenClusters(Layout layout, ClusterGeometry a, ClusterGeometry b,
                                               Box from, Box to) {
        boolean downward = b.centerY() > a.centerY();
        double exitAngle = downward ? -Math.PI / 2 : Math.PI / 2;
        double entryAngle = downward ? Math.PI / 2 : -Math.PI / 2;
        int direction = downward ? 1 : -1;

        int exitY = (int) Math.round(a.centerY() + direction * (a.outerRadius() + CLUSTER_GAP / 2.0));
        int entryY = (int) Math.round(b.centerY() - direction * (b.outerRadius() + CLUSTER_GAP / 2.0));
        int gutterX = from.centerX() <= layout.width() / 2 ? GUTTER / 2 : layout.width() - GUTTER / 2;

        double leave = a.gapOutside(from.ring());
        double arrive = b.gapOutside(to.ring());

        List<Point> route = new ArrayList<>();
        route.add(borderPoint(from, true));
        route.add(polar(a, from.angle(), leave));
        addArc(route, a, from.angle(), exitAngle, leave);
        route.add(new Point(a.centerX(), exitY));
        route.add(new Point(gutterX, exitY));
        route.add(new Point(gutterX, entryY));
        route.add(new Point(b.centerX(), entryY));
        route.add(polar(b, entryAngle, arrive));
        addArc(route, b, entryAngle, to.angle(), arrive);
        route.add(borderPoint(to, true));
        return route;
    }

    /**
     * Where the ray from the hub through a box's centre meets the box's border.
     *
     * <p>Edges stop at the border and never at the centre (§8) — a line drawn to
     * the centre crosses the box and the text inside it, which is one of the three
     * things #136 exists to stop.</p>
     */
    private static Point borderPoint(Box box, boolean outward) {
        return borderPoint(box.centerX(), box.centerY(), box.width(), box.height(), box.angle(), outward);
    }

    private static Point borderPoint(int centerX, int centerY, int width, int height, double angle,
                                     boolean outward) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double t = Double.MAX_VALUE;
        if (Math.abs(cos) > 1e-9) {
            t = Math.min(t, width / 2.0 / Math.abs(cos));
        }
        if (Math.abs(sin) > 1e-9) {
            t = Math.min(t, height / 2.0 / Math.abs(sin));
        }
        if (t == Double.MAX_VALUE) {
            t = 0;
        }
        double step = outward ? t : -t;
        return new Point((int) Math.round(centerX + step * cos),
                (int) Math.round(centerY - step * sin));
    }

    private static Point polar(ClusterGeometry hub, double angle, double radius) {
        return new Point((int) Math.round(hub.centerX() + radius * Math.cos(angle)),
                (int) Math.round(hub.centerY() - radius * Math.sin(angle)));
    }

    /**
     * Adds an arc as a run of short chords.
     *
     * <p>Chord length is bounded rather than the segment count fixed, so a long
     * arc at a wide radius does not turn into a visible polygon while a short one
     * close in does not cost twenty line calls to draw.</p>
     */
    private static void addArc(List<Point> route, ClusterGeometry hub, double fromAngle,
                               double toAngle, double radius) {
        double sweep = deviation(toAngle, fromAngle);
        int steps = Math.clamp((long) Math.ceil(Math.abs(sweep) * radius / 12.0), 1, 48);
        for (int i = 1; i <= steps; i++) {
            route.add(polar(hub, fromAngle + sweep * i / steps, radius));
        }
    }
}
