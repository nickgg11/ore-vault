package com.orevault.orevault.client.screen;

import java.util.ArrayList;
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

        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        drawSpine(graphics, placed, area);
        drawEdges(graphics, area, tiers);
        for (Anchor anchor : placed.anchors()) {
            drawAnchor(graphics, font, area, anchor, spent);
        }
        NodeDef hovered = null;
        for (NodeDef def : nodes) {
            if (drawNode(graphics, font, placed, area, def, tiers, active, teamLevel, points, spent,
                    mouseX, mouseY)) {
                hovered = def;
            }
        }
        graphics.disableScissor();

        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font,
                    tooltip(hovered, tiers, active, teamLevel, points, spent), mouseX, mouseY);
        }
    }

    /**
     * Opens on the first hub rather than on the top-left corner.
     *
     * <p>A hub tree is centred on a spine, so the corner of its bounding box is
     * empty space. Landing there reads as an empty page until you drag, which is
     * a poor first frame for a screen whose whole job is to show a tree.</p>
     */
    private void centreOnFirstDraw(ScreenRectangle area) {
        if (centred || layout == null || layout.anchors().isEmpty()) {
            return;
        }
        centred = true;
        Anchor first = layout.anchors().getFirst();
        scrollX = first.centerX() + PADDING - area.width() / 2;
        scrollY = first.centerY() + PADDING - area.height() / 2;
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
    private void drawSpine(GuiGraphicsExtractor graphics, TreeLayout.Layout placed, ScreenRectangle area) {
        List<Anchor> anchors = placed.anchors();
        for (int i = 0; i + 1 < anchors.size(); i++) {
            stroke(graphics, area,
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
     */
    private void drawEdges(GuiGraphicsExtractor graphics, ScreenRectangle area,
                           Map<String, Integer> tiers) {
        for (Wire wire : wires) {
            boolean met = wire.sourceId() == null
                    ? tiers.getOrDefault(wire.targetId(), 0) > 0
                    : tiers.getOrDefault(wire.sourceId(), 0) >= wire.minTier();
            stroke(graphics, area, wire.route(), met ? COLOR_EDGE_MET : COLOR_EDGE);
        }
    }

    /**
     * A cluster's hub.
     *
     * <p>Deliberately not a node: it is drawn as a doubled frame at the centre of
     * its cluster, it carries the points-spent gate rather than a cost, and
     * {@link #nodeAt} cannot return it, so there is no path by which a click
     * reaches one.</p>
     */
    private void drawAnchor(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, Anchor anchor,
                            int spent) {
        int x = screenX(area, anchor.x());
        int y = screenY(area, anchor.y());
        if (y + anchor.height() < area.top() || y > area.bottom()
                || x + anchor.width() < area.left() || x > area.right()) {
            return;
        }
        boolean open = spent >= anchor.gate();
        int color = open ? COLOR_ANCHOR_OPEN : COLOR_ANCHOR_SHUT;

        graphics.fill(x - 2, y - 2, x + anchor.width() + 2, y + anchor.height() + 2, COLOR_ANCHOR_FILL);
        graphics.outline(x - 2, y - 2, anchor.width() + 4, anchor.height() + 4, color);
        graphics.outline(x, y, anchor.width(), anchor.height(), color);

        int textY = y + (anchor.height() - (font.lineHeight * 2 + 2)) / 2;
        graphics.centeredText(font, Component.literal(anchor.cluster().displayName()),
                x + anchor.width() / 2, textY, color);
        graphics.centeredText(font, Component.translatable(
                        open ? "screen.orevault.tome.anchor.open" : "screen.orevault.tome.anchor.shut",
                        spent, anchor.gate()),
                x + anchor.width() / 2, textY + font.lineHeight + 2,
                open ? COLOR_UNLOCKED : COLOR_LOCKED);
    }

    /**
     * Draws one route.
     *
     * <p>Segments are arbitrary now that routes arc around a hub, so each is
     * rasterised rather than handed to {@code horizontalLine}. Anything wholly
     * off-screen is dropped before it costs a pixel: a full tree is a few thousand
     * segments and only a couple of clusters are ever in view.</p>
     */
    private void stroke(GuiGraphicsExtractor graphics, ScreenRectangle area, List<Point> route, int color) {
        for (int i = 0; i + 1 < route.size(); i++) {
            Point a = route.get(i);
            Point b = route.get(i + 1);
            line(graphics, area, screenX(area, a.x()), screenY(area, a.y()),
                    screenX(area, b.x()), screenY(area, b.y()), color);
        }
    }

    /** One segment, axis-aligned where it can be and stepped where it cannot. */
    private void line(GuiGraphicsExtractor graphics, ScreenRectangle area, int x0, int y0, int x1, int y1,
                      int color) {
        if (Math.max(x0, x1) < area.left() || Math.min(x0, x1) > area.right()
                || Math.max(y0, y1) < area.top() || Math.min(y0, y1) > area.bottom()) {
            return;
        }
        if (x0 == x1) {
            graphics.verticalLine(x0, Math.min(y0, y1) - 1, Math.max(y0, y1), color);
            return;
        }
        if (y0 == y1) {
            graphics.horizontalLine(Math.min(x0, x1), Math.max(x0, x1), y0, color);
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
            graphics.fill(x, y, x + 1, y + 1, color);
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

    /** Draws one node; returns whether the pointer is over it. */
    private boolean drawNode(GuiGraphicsExtractor graphics, Font font, TreeLayout.Layout placed,
                             ScreenRectangle area, NodeDef def, Map<String, Integer> tiers,
                             Set<String> active, int teamLevel, int points, int spent,
                             int mouseX, int mouseY) {
        Box box = placed.box(def.id());
        if (box == null) {
            return false;
        }
        int x = screenX(area, box.x());
        int y = screenY(area, box.y());
        if (y + box.height() < area.top() || y > area.bottom()
                || x + box.width() < area.left() || x > area.right()) {
            return false;
        }

        int tier = tiers.getOrDefault(def.id(), 0);
        int border = borderColor(def, tiers, active, teamLevel, points, spent);
        graphics.fill(x, y, x + box.width(), y + box.height(), COLOR_NODE_FILL);
        graphics.outline(x, y, box.width(), box.height(), border);
        if (isEmphasised(def.nodeClass())) {
            // Keystones, pacts and notables get a second ring rather than a
            // different fill, so class survives every purchase state.
            graphics.outline(x + 1, y + 1, box.width() - 2, box.height() - 2, classColor(def.nodeClass()));
        }

        // The box was measured from every line it can draw, so both lines fit.
        graphics.text(font, displayName(def), x + TreeLayout.TEXT_PADDING, y + 4, border);
        graphics.text(font, detail(def, tier), x + TreeLayout.TEXT_PADDING, y + 4 + font.lineHeight + 2,
                tier >= def.maxTier() ? COLOR_MAXED : COLOR_BODY);

        if (def.tradeoff() && tier > 0) {
            graphics.fill(x + box.width() - 9, y + 4, x + box.width() - 4, y + 9,
                    active.contains(def.id()) ? COLOR_TRADEOFF_ON : COLOR_LOCKED);
        }
        if (def.isExclusive() && tiers.getOrDefault(def.exclusiveWith(), 0) > 0) {
            graphics.text(font, Component.literal("x"), x + box.width() - 10, y + 3, COLOR_LOCKED);
        }

        return mouseX >= x && mouseX < x + box.width() && mouseY >= y && mouseY < y + box.height();
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
    private @Nullable String lockReason(NodeDef def, Map<String, Integer> tiers, int teamLevel, int points,
                                        int spent) {
        int tier = tiers.getOrDefault(def.id(), 0);
        if (tier >= def.maxTier()) {
            return "screen.orevault.tome.node.locked.maxed";
        }
        for (Prereq prereq : def.prereqs()) {
            if (tiers.getOrDefault(prereq.nodeId(), 0) < prereq.minTier()) {
                return "screen.orevault.tome.node.locked.prereq";
            }
        }
        if (spent < NodeDefs.anchorGate(def.cluster())) {
            return "screen.orevault.tome.node.locked.anchor";
        }
        if (def.nodeClass() == NodeClass.FORK_OPTION) {
            for (NodeDef sibling : NodeDefs.forkOptions(def.forkParentId())) {
                if (!sibling.id().equals(def.id()) && tiers.getOrDefault(sibling.id(), 0) > 0) {
                    return "screen.orevault.tome.node.locked.fork";
                }
            }
            return null; // free, so neither level nor points can stand in the way
        }
        if (def.isExclusive() && tiers.getOrDefault(def.exclusiveWith(), 0) > 0) {
            return "screen.orevault.tome.node.locked.exclusive";
        }
        if (teamLevel < def.levelReqs()[tier]) {
            return "screen.orevault.tome.node.locked.level";
        }
        if (points < def.costs()[tier]) {
            return "screen.orevault.tome.node.locked.points";
        }
        return null;
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

        String reason = lockReason(def, tiers, teamLevel, points, spent);
        if (reason != null && tier < def.maxTier()) {
            Component line = reason.endsWith(".anchor")
                    ? Component.translatable(reason, NodeDefs.anchorGate(def.cluster()), spent)
                    : Component.translatable(reason);
            lines.add(line.copy().withStyle(ChatFormatting.RED));
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
        int step = TreeLayout.NODE_HEIGHT + TreeLayout.RING_GAP;
        scrollY -= (int) Math.round(scrollDeltaY * step / 2.0);
        scrollX -= (int) Math.round(scrollDeltaX * step / 2.0);
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
        for (NodeDef def : nodes) {
            Box box = layout.box(def.id());
            if (box == null) {
                continue;
            }
            int x = screenX(area, box.x());
            int y = screenY(area, box.y());
            if (mouseX >= x && mouseX < x + box.width() && mouseY >= y && mouseY < y + box.height()) {
                return def;
            }
        }
        return null;
    }

    // ----- geometry -----

    private int screenX(ScreenRectangle area, int treeX) {
        return area.left() + PADDING + treeX - scrollX;
    }

    private int screenY(ScreenRectangle area, int treeY) {
        return area.top() + PADDING + treeY - scrollY;
    }

    private int contentWidth() {
        return layout == null ? 0 : layout.width() + PADDING * 2;
    }

    private int contentHeight() {
        return layout == null ? 0 : layout.height() + PADDING * 2;
    }

    /**
     * Keeps the tree on screen.
     *
     * <p>When it is smaller than the area the scroll is pinned to 0 rather than
     * allowed to go negative, so a short tree sits at the top-left instead of
     * drifting off it.</p>
     */
    private void clampScroll(ScreenRectangle area) {
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, contentWidth() - area.width())));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight() - area.height())));
    }
}
