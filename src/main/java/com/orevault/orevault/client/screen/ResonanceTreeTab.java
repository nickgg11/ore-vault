package com.orevault.orevault.client.screen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.client.ClientPacketHandlers;
import com.orevault.orevault.network.ModNetwork;
import com.orevault.orevault.network.ModNetwork.SyncSkillTree;
import com.orevault.orevault.network.ModNetwork.SyncTeamProgress;
import com.orevault.orevault.skill.Cluster;
import com.orevault.orevault.skill.NodeClass;
import com.orevault.orevault.skill.NodeDef;
import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.NodeDefs;
import com.orevault.orevault.skill.TreeLayout;
import com.orevault.orevault.skill.TreeLayout.Anchor;
import com.orevault.orevault.skill.TreeLayout.Box;
import com.orevault.orevault.skill.TreeLayout.Point;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Tab 1 of the Tome: the Resonance skill tree, drawn as hubs (§8, #136, #147).
 *
 * <h2>Nothing here decides anything</h2>
 *
 * <p>Every state this draws is derived from what the server last sent —
 * {@link ClientPacketHandlers#skillTree()} for unlocked tiers and the player's
 * tradeoffs, {@link ClientPacketHandlers#teamProgress()} for level and unspent
 * points. A click sends an intent and waits; the node does not light up until a
 * sync says it did. That is slower to feel than an optimistic update and it is
 * the only version that cannot end up showing a purchase the server refused.</p>
 *
 * <h2>What this class owns</h2>
 *
 * <p>Placement is {@link TreeLayout}'s and is unit tested. What is left here is
 * drawing, and three parts of it were playtest findings in their own right:</p>
 *
 * <ul>
 *   <li>The page is drawn <b>opaque</b>. It used to be the screen's own
 *       translucent backdrop, which left the edges competing with whatever the
 *       player happened to be standing in front of. A skill tree that is only
 *       readable while facing a wall is not readable.</li>
 *   <li>Edges are stroked as arbitrary segments rather than as axis-aligned
 *       runs, because the routes are arcs around a hub now.</li>
 *   <li>A box is measured from <b>every line it can ever draw</b>, not from its
 *       name. The second line carries the tier, the cost and the level, and for
 *       a fork parent it carries an option's name, any of which can be longer
 *       than the node is called.</li>
 * </ul>
 *
 * <h2>Panning, and why a click is decided on release</h2>
 *
 * <p>The tree is larger than the screen in both directions at any sane GUI
 * scale, so dragging pans it. A drag begins with the same button-down as a
 * purchase, so the purchase fires on <em>release</em>, and only if the pointer
 * never travelled far enough to count as a drag. Deciding on press instead would
 * buy a node every time someone grabbed the canvas next to one.</p>
 */
public final class ResonanceTreeTab implements TomeTab {

    private static final int PADDING = 12;

    /** Pointer travel, in pixels, past which a press is a pan rather than a click. */
    private static final int DRAG_SLOP = 4;

    /**
     * How far the tree can be pushed away.
     *
     * <p>The page is a little under a thousand pixels across and six thousand
     * down, which is more canvas than panning alone is a reasonable way to cross.
     * A fifth of full size puts a whole cluster on screen at once and the run of
     * hubs within a couple of drags.</p>
     */
    private static final float MIN_ZOOM = 0.20f;
    private static final float MAX_ZOOM = 1.50f;
    private static final float ZOOM_STEP = 1.12f;

    /**
     * Zoom levels below which a node's lines stop being drawn.
     *
     * <p>Three-pixel text is not small text, it is noise laid over the shape the
     * player zoomed out to see. The boxes, their state colours and the edges
     * between them carry the structure on their own.</p>
     */
    private static final float DETAIL_ZOOM = 0.55f;
    private static final float NAME_ZOOM = 0.36f;

    private static final int COLOR_MAXED = 0xFFFFC44F;
    private static final int COLOR_UNLOCKED = 0xFF6FCF6F;
    private static final int COLOR_AVAILABLE = 0xFFE8E8E8;
    private static final int COLOR_LOCKED = 0xFF6E6E6E;
    private static final int COLOR_TRADEOFF_ON = 0xFF4FC3F7;
    private static final int COLOR_BODY = 0xFFA6A6A6;

    /**
     * The page.
     *
     * <p>Fully opaque on purpose. The tree used to be drawn straight onto the
     * screen's translucent backdrop, and the edges — thin, dark and one pixel
     * wide — disappeared into whatever the world behind happened to be.</p>
     */
    private static final int COLOR_PAGE = 0xFF13110D;
    private static final int COLOR_PAGE_EDGE = 0xFF2A2519;
    private static final int COLOR_NODE_FILL = 0xFF1D1A13;

    private static final int COLOR_EDGE = 0xFF6B6152;
    private static final int COLOR_EDGE_MET = 0xFF7FC77F;
    private static final int COLOR_SPINE = 0xFF8A7A4E;

    /**
     * The hovered node's whole chain of prerequisites.
     *
     * <p>An outer-ring node is several rings and an arc away from what unlocks
     * it, and reading that off a static picture means tracing a line by eye.
     * Hovering lights the entire path back to the hub instead.</p>
     */
    private static final int COLOR_CHAIN = 0xFFFFD98A;

    // Per-class treatment (§8). A node's class is the first thing a reader
    // should be able to tell without a tooltip, so it is carried by the border
    // rather than by a badge that competes with the tradeoff and exclusion marks.
    private static final int COLOR_KEYSTONE = 0xFFD98A3A;
    private static final int COLOR_PACT = 0xFFC0563C;
    private static final int COLOR_GROWTH = 0xFF6FB7A8;
    private static final int COLOR_NOTABLE = 0xFFB9A25E;
    private static final int COLOR_FORK = 0xFF8E7BB5;

    private static final int COLOR_ANCHOR_OPEN = 0xFFCFC49A;
    private static final int COLOR_ANCHOR_SHUT = 0xFF6A6250;
    private static final int COLOR_ANCHOR_FILL = 0xFF231D10;

    /** Widest point total a hub's gate line can be asked to render. */
    private static final int GATE_MEASURE = 9999;

    private final Component title;
    private final List<NodeDef> nodes;

    private TreeLayout.@Nullable Layout layout;
    private List<Wire> wires = List.of();

    private int scrollX;
    private int scrollY;
    private float zoom = 1.0f;
    private boolean centred;
    private boolean pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;

    public ResonanceTreeTab(Component title) {
        this.title = title;
        this.nodes = visibleNodes();
    }

    /**
     * The Resonance nodes this client should draw.
     *
     * <p>Ultimine nodes disappear entirely rather than showing as locked (§8):
     * a permanently unreachable node in a skill tree reads as a bug, not as a
     * feature belonging to a mod you have not installed.</p>
     *
     * <p>The check is against the client's own mod list, which is the honest
     * answer for singleplayer and for any server that requires the same mods.
     * {@code SoftDeps} ([41], #42) replaces this with the shared helper, and if
     * a server can ever disagree with its clients about Ultimine the answer has
     * to come down in the sync packet instead.</p>
     */
    private static List<NodeDef> visibleNodes() {
        boolean ultimine = ModList.get().isLoaded("ftbultimine");
        List<NodeDef> visible = new ArrayList<>();
        for (NodeDef def : NodeDefs.getByTree(Tree.RESONANCE)) {
            if (def.ultimineOnly() && !ultimine) {
                continue;
            }
            visible.add(def);
        }
        return List.copyOf(visible);
    }

    /**
     * The placement, built on first draw rather than in the constructor.
     *
     * <p>Layout needs to measure text, and the font is a screen-time resource.
     * Building here also means the tree is measured once per screen rather than
     * once per frame — {@link TreeLayout#of} walks every node several times.</p>
     */
    private TreeLayout.Layout layout(Font font) {
        if (layout == null) {
            layout = TreeLayout.of(nodes,
                    def -> widestLine(font, def),
                    cluster -> widestHubLine(font, cluster));
            wires = routes(layout);
        }
        return layout;
    }

    /**
     * One drawn line, with what decides its colour.
     *
     * <p>{@code sourceId} is the prerequisite; {@code null} means a spoke from the
     * hub, which is lit once the node it reaches has been bought at all.</p>
     */
    private record Wire(List<Point> route, @Nullable String sourceId, int minTier, String targetId) {
    }

    /**
     * Every route, built once with the layout.
     *
     * <p>A route depends on the placement and nothing else — only its colour
     * moves — and an arc is thirty-odd points, so rebuilding them all per frame
     * meant a few thousand throwaway objects sixty times a second for a picture
     * that never changes.</p>
     */
    private List<Wire> routes(TreeLayout.Layout placed) {
        List<Wire> built = new ArrayList<>();
        for (NodeDef def : nodes) {
            Box to = placed.box(def.id());
            if (to == null) {
                continue;
            }
            for (Prereq prereq : def.prereqs()) {
                Box from = placed.box(prereq.nodeId());
                if (from != null) {
                    built.add(new Wire(TreeLayout.edge(placed, from, to), prereq.nodeId(),
                            prereq.minTier(), def.id()));
                }
            }
            if (def.forkParentId() != null) {
                Box from = placed.box(def.forkParentId());
                if (from != null) {
                    built.add(new Wire(TreeLayout.edge(placed, from, to), def.forkParentId(), 1,
                            def.id()));
                }
            }
            List<Point> spoke = TreeLayout.spoke(placed, to);
            if (!spoke.isEmpty()) {
                built.add(new Wire(spoke, null, 1, def.id()));
            }
        }
        return List.copyOf(built);
    }

    /**
     * The widest line this node's box will ever have to draw.
     *
     * <p>Every second line the node can show is measured, not the one it happens
     * to show right now, because the layout is built once and a box that resized
     * on purchase would move its neighbours. A fork parent is the case that
     * matters: once specialised its second line is the chosen option's display
     * name, which is routinely longer than the parent's own.</p>
     */
    private static int widestLine(Font font, NodeDef def) {
        int widest = font.width(displayName(def));
        widest = Math.max(widest, font.width(
                Component.translatable("screen.orevault.tome.node.maxed", def.maxTier())));
        for (int tier = 0; tier < def.maxTier(); tier++) {
            widest = Math.max(widest, font.width(Component.translatable(
                    "screen.orevault.tome.node.tier", tier, def.maxTier(),
                    def.costs()[tier], def.levelReqs()[tier])));
        }
        if (def.nodeClass() == NodeClass.FORK_PARENT) {
            widest = Math.max(widest,
                    font.width(Component.translatable("screen.orevault.tome.node.inert")));
            for (NodeDef option : NodeDefs.forkOptions(def.id())) {
                widest = Math.max(widest, font.width(Component.translatable(
                        "screen.orevault.tome.node.specialised", displayName(option))));
            }
        }
        if (def.nodeClass() == NodeClass.FORK_OPTION) {
            widest = Math.max(widest,
                    font.width(Component.translatable("screen.orevault.tome.node.option_free")));
            widest = Math.max(widest,
                    font.width(Component.translatable("screen.orevault.tome.node.option_taken")));
        }
        return widest;
    }

    /** The widest of a hub's two lines: the cluster's name, and its gate either way round. */
    private static int widestHubLine(Font font, Cluster cluster) {
        int widest = font.width(Component.literal(cluster.displayName()));
        for (String key : new String[] {"screen.orevault.tome.anchor.open",
                "screen.orevault.tome.anchor.shut"}) {
            widest = Math.max(widest, font.width(
                    Component.translatable(key, GATE_MEASURE, GATE_MEASURE)));
        }
        return widest;
    }

    // ----- Tab -----

    @Override
    public Component getTabTitle() {
        return title;
    }

    @Override
    public Component getTabExtraNarration() {
        return Component.translatable("screen.orevault.tome.tree.narration", nodes.size());
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
        // The tree is drawn, not built from widgets; see TomeTab's input methods.
    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {
        clampScroll(screenRectangle);
    }

    // ----- drawing -----

    @Override
    public void drawContent(GuiGraphicsExtractor graphics, ScreenRectangle area, int mouseX, int mouseY,
                            float partialTick) {
        Font font = Minecraft.getInstance().font;
        graphics.fill(area.left(), area.top(), area.right(), area.bottom(), COLOR_PAGE);
        graphics.horizontalLine(area.left(), area.right(), area.top(), COLOR_PAGE_EDGE);

        SyncSkillTree tree = ClientPacketHandlers.skillTree();
        SyncTeamProgress progress = ClientPacketHandlers.teamProgress();
        if (tree == null || progress == null) {
            graphics.centeredText(font, Component.translatable("screen.orevault.tome.syncing"),
                    area.left() + area.width() / 2, area.top() + area.height() / 2 - font.lineHeight / 2,
                    COLOR_BODY);
            return;
        }

        TreeLayout.Layout placed = layout(font);
        centreOnFirstDraw(area);
        clampScroll(area);
        Map<String, Integer> tiers = tree.resonanceTiers();
        Set<String> active = Set.copyOf(tree.activeTradeoffs());
        int teamLevel = progress.resonance().level();
        int points = progress.resonance().unspentPoints();
        int spent = pointsSpent(tiers);

        NodeDef hovered = nodeAt(area, mouseX, mouseY);
        Set<String> chain = chainOf(hovered);
        View view = view(area);

        // The scissor is taken before the zoom, so it stays the screen rectangle
        // this tab was handed. Everything after is drawn in tree coordinates and
        // scaled into place.
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(originX(area), originY(area));
        graphics.pose().scale(zoom, zoom);

        drawSpine(graphics, placed, view);
        drawEdges(graphics, view, tiers, chain);
        for (Anchor anchor : placed.anchors()) {
            drawAnchor(graphics, font, view, anchor, spent);
        }
        for (NodeDef def : nodes) {
            drawNode(graphics, font, placed, view, def, tiers, active, teamLevel, points, spent, chain);
        }

        graphics.pose().popMatrix();
        graphics.disableScissor();

        drawZoomReadout(graphics, font, area);
        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font,
                    tooltip(hovered, tiers, active, teamLevel, points, spent), mouseX, mouseY);
        }
    }

    /**
     * The part of the tree on screen, in tree coordinates.
     *
     * <p>Culling has to happen in the same space the drawing does, and after the
     * zoom that is no longer the screen rectangle. Zoomed out, the page is a few
     * thousand line segments and all but a couple of clusters' worth are off the
     * edge of it.</p>
     */
    private record View(double left, double top, double right, double bottom) {

        boolean showsBox(int x, int y, int width, int height) {
            return x + width >= left && x <= right && y + height >= top && y <= bottom;
        }

        boolean showsSegment(int x0, int y0, int x1, int y1) {
            return Math.max(x0, x1) >= left && Math.min(x0, x1) <= right
                    && Math.max(y0, y1) >= top && Math.min(y0, y1) <= bottom;
        }
    }

    private float originX(ScreenRectangle area) {
        return area.left() + PADDING - scrollX;
    }

    private float originY(ScreenRectangle area) {
        return area.top() + PADDING - scrollY;
    }

    private View view(ScreenRectangle area) {
        return new View((area.left() - originX(area)) / zoom, (area.top() - originY(area)) / zoom,
                (area.right() - originX(area)) / zoom, (area.bottom() - originY(area)) / zoom);
    }

    /**
     * How thick a one-pixel line has to be drawn to stay one pixel on screen.
     *
     * <p>Lines are drawn in tree coordinates and scaled down with everything
     * else, so at a fifth of full size a hairline is a fifth of a pixel and
     * simply is not there. Zooming out to see the shape of the tree and losing
     * the lines that give it that shape would be a poor trade.</p>
     */
    private int lineWeight() {
        return Math.max(1, Math.round(1f / zoom));
    }

    /**
     * Every node the hovered one waits on, however far back.
     *
     * <p>Not only its immediate prerequisites. A node on an outer ring is reached
     * through a chain, and the question a player is actually asking — what do I
     * have to buy to get this — is answered by the whole path back to the hub
     * rather than by the last step of it.</p>
     */
    private Set<String> chainOf(@Nullable NodeDef hovered) {
        if (hovered == null) {
            return Set.of();
        }
        Set<String> chain = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(hovered.id());
        while (!pending.isEmpty()) {
            String id = pending.poll();
            if (!chain.add(id)) {
                continue;
            }
            NodeDef def = NodeDefs.get(id);
            if (def == null) {
                continue;
            }
            for (Prereq prereq : def.prereqs()) {
                pending.add(prereq.nodeId());
            }
            if (def.forkParentId() != null) {
                pending.add(def.forkParentId());
            }
        }
        return chain;
    }

    /**
     * Opens on the first hub rather than on the top-left corner.
     *
     * <p>A hub tree is centred on a spine, so the corner of its bounding box is
     * empty space. Landing there reads as an empty page until you drag, which is
     * a poor first frame for a screen whose whole job is to show a tree.</p>
     */
    private void centreOnFirstDraw(ScreenRectangle area) {
        if (centred || layout == null || layout.anchors().isEmpty() || area.width() <= 0) {
            return;
        }
        centred = true;
        Anchor first = layout.anchors().getFirst();
        scrollX = Math.round(PADDING + first.centerX() * zoom - area.width() / 2f);
        scrollY = Math.round(PADDING + first.centerY() * zoom - area.height() / 2f);
    }

    /** Says how far out the tree is, so the zoom is discoverable at all. */
    private void drawZoomReadout(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area) {
        graphics.text(font,
                Component.translatable("screen.orevault.tome.zoom", Math.round(zoom * 100)),
                area.left() + 6, area.bottom() - font.lineHeight - 5, COLOR_LOCKED);
    }

    /**
     * Skill points the team has put into the tree.
     *
     * <p>Mirrors {@code SkillTree#skillPointsInvested}. The client holds a tier
     * map rather than a {@code SkillTree}, and this only decides whether an
     * anchor is drawn open — the server re-derives it before allowing anything.</p>
     */
    private int pointsSpent(Map<String, Integer> tiers) {
        int total = 0;
        for (Map.Entry<String, Integer> entry : tiers.entrySet()) {
            NodeDef def = NodeDefs.get(entry.getKey());
            if (def == null) {
                continue;
            }
            int tier = Math.min(entry.getValue(), def.maxTier());
            for (int i = 0; i < tier; i++) {
                total += def.costs()[i];
            }
        }
        return total;
    }

    /**
     * The line joining one hub to the next.
     *
     * <p>Drawn first and underneath everything, in the corridor {@link TreeLayout}
     * keeps clear above and below every hub. It is what makes the clusters read
     * as an order to work down rather than as a scattering of islands.</p>
     */
    private void drawSpine(GuiGraphicsExtractor graphics, TreeLayout.Layout placed, View view) {
        List<Anchor> anchors = placed.anchors();
        for (int i = 0; i + 1 < anchors.size(); i++) {
            stroke(graphics, view,
                    TreeLayout.spine(placed, anchors.get(i).cluster(), anchors.get(i + 1).cluster()),
                    COLOR_SPINE);
        }
    }

    /**
     * Prerequisite edges and hub spokes.
     *
     * <p>The routes come from {@link TreeLayout}, which starts and ends them on a
     * box border and only ever runs along a node's own ray, through the gap
     * between two rings, or up a corridor. The grid this replaces drew a straight
     * elbow between box centres, which is how a line ended up crossing the text of
     * every node in between.</p>
     *
     * <p>A spoke joins a first-ring node to its hub. Nothing else holds such a
     * node back but the cluster's own gate, and drawing the spoke is what makes a
     * cluster read as radiating from its anchor rather than merely surrounding
     * it.</p>
     *
     * <p>Whatever is on the hovered node's chain is drawn last, so it sits over
     * the edges it crosses instead of under them.</p>
     */
    private void drawEdges(GuiGraphicsExtractor graphics, View view, Map<String, Integer> tiers,
                           Set<String> chain) {
        List<Wire> lit = new ArrayList<>();
        for (Wire wire : wires) {
            if (onChain(wire, chain)) {
                lit.add(wire);
                continue;
            }
            boolean met = wire.sourceId() == null
                    ? tiers.getOrDefault(wire.targetId(), 0) > 0
                    : tiers.getOrDefault(wire.sourceId(), 0) >= wire.minTier();
            stroke(graphics, view, wire.route(), met ? COLOR_EDGE_MET : COLOR_EDGE);
        }
        for (Wire wire : lit) {
            stroke(graphics, view, wire.route(), COLOR_CHAIN);
        }
    }

    private static boolean onChain(Wire wire, Set<String> chain) {
        return chain.contains(wire.targetId())
                && (wire.sourceId() == null || chain.contains(wire.sourceId()));
    }

    /**
     * A cluster's hub.
     *
     * <p>Deliberately not a node: it is drawn as a doubled frame at the centre of
     * its cluster, it carries the points-spent gate rather than a cost, and
     * {@link #nodeAt} cannot return it, so there is no path by which a click
     * reaches one.</p>
     */
    private void drawAnchor(GuiGraphicsExtractor graphics, Font font, View view, Anchor anchor,
                            int spent) {
        if (!view.showsBox(anchor.x() - 2, anchor.y() - 2, anchor.width() + 4, anchor.height() + 4)) {
            return;
        }
        int x = anchor.x();
        int y = anchor.y();
        boolean open = spent >= anchor.gate();
        int color = open ? COLOR_ANCHOR_OPEN : COLOR_ANCHOR_SHUT;

        graphics.fill(x - 2, y - 2, x + anchor.width() + 2, y + anchor.height() + 2, COLOR_ANCHOR_FILL);
        frame(graphics, x - 2, y - 2, anchor.width() + 4, anchor.height() + 4, color);
        frame(graphics, x, y, anchor.width(), anchor.height(), color);
        if (zoom < NAME_ZOOM) {
            return;
        }

        int textY = y + (anchor.height() - (font.lineHeight * 2 + 2)) / 2;
        graphics.centeredText(font, Component.literal(anchor.cluster().displayName()),
                x + anchor.width() / 2, textY, color);
        graphics.centeredText(font, Component.translatable(
                        open ? "screen.orevault.tome.anchor.open" : "screen.orevault.tome.anchor.shut",
                        spent, anchor.gate()),
                x + anchor.width() / 2, textY + font.lineHeight + 2,
                open ? COLOR_UNLOCKED : COLOR_LOCKED);
    }

    /** A rectangle outline that survives being zoomed out. */
    private void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int weight = lineWeight();
        graphics.fill(x, y, x + width, y + weight, color);
        graphics.fill(x, y + height - weight, x + width, y + height, color);
        graphics.fill(x, y, x + weight, y + height, color);
        graphics.fill(x + width - weight, y, x + width, y + height, color);
    }

    /**
     * Draws one route.
     *
     * <p>Segments are arbitrary now that routes arc around a hub, so each is
     * rasterised rather than handed to {@code horizontalLine}. Anything wholly out
     * of view is dropped before it costs a pixel: the page is a few thousand
     * segments and only a couple of clusters are ever on screen.</p>
     */
    private void stroke(GuiGraphicsExtractor graphics, View view, List<Point> route, int color) {
        int weight = lineWeight();
        for (int i = 0; i + 1 < route.size(); i++) {
            Point a = route.get(i);
            Point b = route.get(i + 1);
            if (view.showsSegment(a.x(), a.y(), b.x(), b.y())) {
                line(graphics, a.x(), a.y(), b.x(), b.y(), weight, color);
            }
        }
    }

    /** One segment, axis-aligned where it can be and stepped where it cannot. */
    private void line(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int weight,
                      int color) {
        if (x0 == x1) {
            graphics.fill(x0, Math.min(y0, y1), x0 + weight, Math.max(y0, y1) + weight, color);
            return;
        }
        if (y0 == y1) {
            graphics.fill(Math.min(x0, x1), y0, Math.max(x0, x1) + weight, y0 + weight, color);
            return;
        }
        int dx = Math.abs(x1 - x0);
        int dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        int x = x0;
        int y = y0;
        while (true) {
            graphics.fill(x, y, x + weight, y + weight, color);
            if (x == x1 && y == y1) {
                return;
            }
            int doubled = error * 2;
            if (doubled >= dy) {
                error += dy;
                x += sx;
            }
            if (doubled <= dx) {
                error += dx;
                y += sy;
            }
        }
    }

    /** Draws one node. */
    private void drawNode(GuiGraphicsExtractor graphics, Font font, TreeLayout.Layout placed,
                          View view, NodeDef def, Map<String, Integer> tiers, Set<String> active,
                          int teamLevel, int points, int spent, Set<String> chain) {
        Box box = placed.box(def.id());
        if (box == null || !view.showsBox(box.x(), box.y(), box.width(), box.height())) {
            return;
        }
        int x = box.x();
        int y = box.y();
        int tier = tiers.getOrDefault(def.id(), 0);
        int border = borderColor(def, tiers, active, teamLevel, points, spent);

        graphics.fill(x, y, x + box.width(), y + box.height(), COLOR_NODE_FILL);
        if (chain.contains(def.id())) {
            // A halo outside the box rather than a recoloured border, so a lit
            // node's own purchase state is still readable while it is lit.
            frame(graphics, x - 2, y - 2, box.width() + 4, box.height() + 4, COLOR_CHAIN);
        }
        frame(graphics, x, y, box.width(), box.height(), border);
        if (isEmphasised(def.nodeClass())) {
            // Keystones, pacts and notables get a second ring rather than a
            // different fill, so class survives every purchase state.
            frame(graphics, x + 1, y + 1, box.width() - 2, box.height() - 2, classColor(def.nodeClass()));
        }
        if (zoom < NAME_ZOOM) {
            return;
        }

        // The box was measured from every line it can draw, so both lines fit.
        graphics.text(font, displayName(def), x + TreeLayout.TEXT_PADDING, y + 4, border);
        if (zoom >= DETAIL_ZOOM) {
            graphics.text(font, detail(def, tier), x + TreeLayout.TEXT_PADDING,
                    y + 4 + font.lineHeight + 2, tier >= def.maxTier() ? COLOR_MAXED : COLOR_BODY);
        }

        if (def.tradeoff() && tier > 0) {
            graphics.fill(x + box.width() - 9, y + 4, x + box.width() - 4, y + 9,
                    active.contains(def.id()) ? COLOR_TRADEOFF_ON : COLOR_LOCKED);
        }
        if (def.isExclusive() && tiers.getOrDefault(def.exclusiveWith(), 0) > 0) {
            graphics.text(font, Component.literal("x"), x + box.width() - 10, y + 3, COLOR_LOCKED);
        }
    }

    /**
     * The second line of a node box.
     *
     * <p>A bought fork parent that has not been specialized says so here rather
     * than in the tooltip only. An inert parent is the state most likely to be
     * mistaken for a bug — it is paid for, it is lit up, and it does nothing.</p>
     */
    private Component detail(NodeDef def, int tier) {
        if (def.nodeClass() == NodeClass.FORK_PARENT && tier > 0) {
            NodeDef chosen = chosenOption(def);
            return chosen == null
                    ? Component.translatable("screen.orevault.tome.node.inert")
                    : Component.translatable("screen.orevault.tome.node.specialised",
                            displayName(chosen));
        }
        if (def.nodeClass() == NodeClass.FORK_OPTION) {
            return Component.translatable(tier > 0
                    ? "screen.orevault.tome.node.option_taken"
                    : "screen.orevault.tome.node.option_free");
        }
        return tier >= def.maxTier()
                ? Component.translatable("screen.orevault.tome.node.maxed", tier)
                : Component.translatable("screen.orevault.tome.node.tier", tier, def.maxTier(),
                        def.costs()[tier], def.levelReqs()[tier]);
    }

    /** The fork option currently held under {@code parent}, or {@code null} if none is. */
    private @Nullable NodeDef chosenOption(NodeDef parent) {
        SyncSkillTree tree = ClientPacketHandlers.skillTree();
        if (tree == null) {
            return null;
        }
        for (NodeDef option : NodeDefs.forkOptions(parent.id())) {
            if (tree.resonanceTiers().getOrDefault(option.id(), 0) > 0) {
                return option;
            }
        }
        return null;
    }

    private static boolean isEmphasised(NodeClass nodeClass) {
        return nodeClass == NodeClass.KEYSTONE || nodeClass == NodeClass.PACT
                || nodeClass == NodeClass.NOTABLE || nodeClass == NodeClass.GROWTH;
    }

    private static int classColor(NodeClass nodeClass) {
        return switch (nodeClass) {
            case KEYSTONE -> COLOR_KEYSTONE;
            case PACT -> COLOR_PACT;
            case GROWTH -> COLOR_GROWTH;
            case NOTABLE -> COLOR_NOTABLE;
            case FORK_PARENT, FORK_OPTION -> COLOR_FORK;
            case SMALL, TRADEOFF -> COLOR_BODY;
        };
    }

    private int borderColor(NodeDef def, Map<String, Integer> tiers, Set<String> active, int teamLevel,
                            int points, int spent) {
        int tier = tiers.getOrDefault(def.id(), 0);
        if (tier >= def.maxTier() && def.nodeClass() != NodeClass.FORK_OPTION) {
            return COLOR_MAXED;
        }
        if (def.tradeoff() && tier > 0 && active.contains(def.id())) {
            return COLOR_TRADEOFF_ON;
        }
        if (tier > 0) {
            return COLOR_UNLOCKED;
        }
        return lockReason(def, tiers, teamLevel, points, spent) == null ? COLOR_AVAILABLE : COLOR_LOCKED;
    }

    /**
     * The lang key explaining why the next tier cannot be bought, or {@code null}
     * if it can.
     *
     * <p>A deliberate re-implementation of {@code SkillTree.canUnlock} rather
     * than a call to it: the client has a tier map, not a {@code SkillTree}, and
     * building one just to ask would invite treating the answer as authoritative.
     * The server checks again and its answer is the one that counts — this only
     * decides whether a node is drawn bright.</p>
     *
     * <p>The order matters and matches the server's. Prerequisites are reported
     * before the anchor so that a locked node names the requirement a player can
     * act on, and the free-option case is settled before the point balance so an
     * empty pool never blocks a pick that costs nothing.</p>
     */
    private @Nullable Lock lockReason(NodeDef def, Map<String, Integer> tiers, int teamLevel, int points,
                                      int spent) {
        int tier = tiers.getOrDefault(def.id(), 0);
        if (tier >= def.maxTier()) {
            return new Lock("screen.orevault.tome.node.locked.maxed");
        }
        for (Prereq prereq : def.prereqs()) {
            if (tiers.getOrDefault(prereq.nodeId(), 0) < prereq.minTier()) {
                return new Lock("screen.orevault.tome.node.locked.prereq",
                        nameOf(prereq.nodeId()), prereq.minTier());
            }
        }
        if (def.forkParentId() != null && tiers.getOrDefault(def.forkParentId(), 0) < 1) {
            return new Lock("screen.orevault.tome.node.locked.prereq", nameOf(def.forkParentId()), 1);
        }
        if (spent < NodeDefs.anchorGate(def.cluster())) {
            return new Lock("screen.orevault.tome.node.locked.anchor",
                    NodeDefs.anchorGate(def.cluster()), spent);
        }
        if (def.nodeClass() == NodeClass.FORK_OPTION) {
            for (NodeDef sibling : NodeDefs.forkOptions(def.forkParentId())) {
                if (!sibling.id().equals(def.id()) && tiers.getOrDefault(sibling.id(), 0) > 0) {
                    return new Lock("screen.orevault.tome.node.locked.fork", displayName(sibling));
                }
            }
            return null; // free, so neither level nor points can stand in the way
        }
        if (def.isExclusive() && tiers.getOrDefault(def.exclusiveWith(), 0) > 0) {
            return new Lock("screen.orevault.tome.node.locked.exclusive", nameOf(def.exclusiveWith()));
        }
        if (teamLevel < def.levelReqs()[tier]) {
            return new Lock("screen.orevault.tome.node.locked.level", def.levelReqs()[tier], teamLevel);
        }
        if (points < def.costs()[tier]) {
            return new Lock("screen.orevault.tome.node.locked.points", def.costs()[tier], points);
        }
        return null;
    }

    /**
     * Why a node cannot be bought, and which node or number is in the way.
     *
     * <p>"Needs an earlier node first" was true and useless: on an outer ring the
     * node it means is several rings and an arc away, and finding it by eye is
     * the thing that was reported as unclear. The reason now names it.</p>
     */
    private record Lock(String key, Object... args) {

        Component text() {
            return Component.translatable(key, args);
        }
    }

    /** A node's display name by id, for a message that has to point at another node. */
    private static Component nameOf(String nodeId) {
        NodeDef def = NodeDefs.get(nodeId);
        return def == null ? Component.literal(nodeId) : displayName(def);
    }

    /**
     * What this node waits on, spelled out.
     *
     * <p>Shown whether or not the node is locked. The tooltip is the only place a
     * player can read the requirement as words rather than trace an arc, and once
     * the node is bought the list is how they see what it cost them to reach.</p>
     */
    private List<Component> requirements(NodeDef def, Map<String, Integer> tiers) {
        List<Component> lines = new ArrayList<>();
        if (def.forkParentId() != null) {
            lines.add(requirementLine(def.forkParentId(), 1, tiers));
        }
        for (Prereq prereq : def.prereqs()) {
            lines.add(requirementLine(prereq.nodeId(), prereq.minTier(), tiers));
        }
        if (lines.isEmpty()) {
            return lines;
        }
        lines.addFirst(Component.translatable("screen.orevault.tome.node.requires")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private Component requirementLine(String nodeId, int minTier, Map<String, Integer> tiers) {
        boolean met = tiers.getOrDefault(nodeId, 0) >= minTier;
        NodeDef other = NodeDefs.get(nodeId);
        String cluster = other == null ? "?" : other.cluster().displayName();
        return Component.translatable("screen.orevault.tome.node.requires.entry",
                        nameOf(nodeId), minTier, cluster)
                .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private List<Component> tooltip(NodeDef def, Map<String, Integer> tiers, Set<String> active,
                                    int teamLevel, int points, int spent) {
        List<Component> lines = new ArrayList<>();
        lines.add(displayName(def).copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("screen.orevault.tome.class." + def.nodeClass().name().toLowerCase())
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("node.orevault." + def.id() + ".desc")
                .withStyle(ChatFormatting.GRAY));

        int tier = tiers.getOrDefault(def.id(), 0);
        lines.add(Component.translatable("screen.orevault.tome.node.owned", tier, def.maxTier())
                .withStyle(ChatFormatting.DARK_GRAY));
        if (tier < def.maxTier()) {
            lines.add(Component.translatable("screen.orevault.tome.node.next",
                    tier + 1, def.costs()[tier], def.levelReqs()[tier]).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (def.nodeClass() == NodeClass.FORK_PARENT && tier > 0 && chosenOption(def) == null) {
            lines.add(Component.translatable("screen.orevault.tome.node.inert.hint")
                    .withStyle(ChatFormatting.YELLOW));
        }
        lines.addAll(requirements(def, tiers));
        int gate = NodeDefs.anchorGate(def.cluster());
        if (gate > 0) {
            lines.add(Component.translatable("screen.orevault.tome.node.requires.cluster",
                            def.cluster().displayName(), gate, spent)
                    .withStyle(spent >= gate ? ChatFormatting.GREEN : ChatFormatting.RED));
        }

        Lock reason = lockReason(def, tiers, teamLevel, points, spent);
        if (reason != null && tier < def.maxTier()) {
            lines.add(reason.text().copy().withStyle(ChatFormatting.RED));
        } else if (reason == null) {
            lines.add(Component.translatable("screen.orevault.tome.node.click_to_buy")
                    .withStyle(ChatFormatting.GREEN));
        }
        if (def.tradeoff() && tier > 0) {
            lines.add(Component.translatable(active.contains(def.id())
                            ? "screen.orevault.tome.node.tradeoff_on"
                            : "screen.orevault.tome.node.tradeoff_off")
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }

    /**
     * A node's name, from lang if translated and from {@code NodeDefs} if not.
     *
     * <p>The fallback matters because effects are still being built out
     * (#139–#145): a node registered without a matching lang key should read as
     * its English name, not as {@code node.orevault.whatever}.</p>
     */
    private static Component displayName(NodeDef def) {
        return Component.translatableWithFallback("node.orevault." + def.id(), def.name());
    }

    // ----- input -----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, ScreenRectangle area) {
        pressed = true;
        dragging = false;
        pressX = event.x();
        pressY = event.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY, ScreenRectangle area) {
        if (!pressed) {
            return false;
        }
        if (Math.abs(event.x() - pressX) > DRAG_SLOP || Math.abs(event.y() - pressY) > DRAG_SLOP) {
            dragging = true;
        }
        if (dragging) {
            scrollX -= (int) Math.round(dragX);
            scrollY -= (int) Math.round(dragY);
            clampScroll(area);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event, ScreenRectangle area) {
        boolean wasClick = pressed && !dragging;
        pressed = false;
        dragging = false;
        if (!wasClick) {
            return false;
        }
        NodeDef target = nodeAt(area, (int) event.x(), (int) event.y());
        if (target == null) {
            return false;
        }
        return activate(target, event.button());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY,
                                 ScreenRectangle area) {
        if (scrollDeltaY == 0) {
            return false;
        }
        float next = Math.clamp(zoom * (float) Math.pow(ZOOM_STEP, scrollDeltaY), MIN_ZOOM, MAX_ZOOM);
        if (next == zoom) {
            return true;
        }
        // Anchored on the pointer: whatever is under it stays under it, which is
        // what makes zooming out to find a cluster and back in on it one gesture
        // rather than a zoom followed by hunting for where it went.
        double treeX = (mouseX - originX(area)) / zoom;
        double treeY = (mouseY - originY(area)) / zoom;
        zoom = next;
        scrollX = (int) Math.round(area.left() + PADDING + treeX * zoom - mouseX);
        scrollY = (int) Math.round(area.top() + PADDING + treeY * zoom - mouseY);
        clampScroll(area);
        return true;
    }

    /**
     * Acts on a clicked node.
     *
     * <p>Left buys the next tier, right flips a purchased tradeoff. Splitting
     * them by button rather than by node state is what makes a maxed-out
     * tradeoff still toggleable — with one button, the last purchase would have
     * taken the toggle away with it.</p>
     */
    private boolean activate(NodeDef def, int button) {
        SyncSkillTree tree = ClientPacketHandlers.skillTree();
        if (tree == null) {
            return false;
        }
        int tier = tree.resonanceTiers().getOrDefault(def.id(), 0);

        if (button == 1) {
            if (!def.tradeoff() || tier == 0) {
                return false;
            }
            ClientPacketDistributor.sendToServer(new ModNetwork.ToggleTradeoff(def.id()));
            return true;
        }
        if (button != 0 || tier >= def.maxTier()) {
            return false;
        }
        // Sent even when the client believes it is not purchasable: the server
        // re-derives everything and refusing is cheap, whereas a client-side
        // veto on stale data is a node that cannot be bought until you reopen
        // the screen.
        ClientPacketDistributor.sendToServer(new ModNetwork.PurchaseNode(def.id()));
        return true;
    }

    /** The node under the pointer. Anchors are not candidates — they cannot be bought. */
    private @Nullable NodeDef nodeAt(ScreenRectangle area, int mouseX, int mouseY) {
        if (layout == null) {
            return null;
        }
        int treeX = (int) Math.floor((mouseX - originX(area)) / zoom);
        int treeY = (int) Math.floor((mouseY - originY(area)) / zoom);
        for (NodeDef def : nodes) {
            Box box = layout.box(def.id());
            if (box != null && box.contains(treeX, treeY)) {
                return def;
            }
        }
        return null;
    }

    // ----- geometry -----

    private int contentWidth() {
        return layout == null ? 0 : Math.round(layout.width() * zoom) + PADDING * 2;
    }

    private int contentHeight() {
        return layout == null ? 0 : Math.round(layout.height() * zoom) + PADDING * 2;
    }

    /**
     * Keeps the tree on screen.
     *
     * <p>Zoomed far enough out the whole tree is smaller than the page, and
     * pinning the scroll to zero there would shove it into the top-left corner —
     * exactly when the player is trying to look at the shape of the whole thing.
     * Below that point the scroll centres it instead.</p>
     */
    private void clampScroll(ScreenRectangle area) {
        scrollX = clampAxis(scrollX, contentWidth(), area.width());
        scrollY = clampAxis(scrollY, contentHeight(), area.height());
    }

    private static int clampAxis(int scroll, int content, int available) {
        if (content <= available) {
            return (content - available) / 2;
        }
        return Math.clamp(scroll, 0, content - available);
    }
}
