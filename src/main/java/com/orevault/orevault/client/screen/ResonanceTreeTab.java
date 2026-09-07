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
import com.orevault.orevault.skill.NodeDef;
import com.orevault.orevault.skill.NodeDef.Prereq;
import com.orevault.orevault.skill.NodeDef.Tree;
import com.orevault.orevault.skill.NodeDefs;
import com.orevault.orevault.skill.TreeLayout;

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
 * Tab 1 of the Tome: the Resonance skill tree as a node graph (§8, [35]).
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
 * <h2>Panning, and why a click is decided on release</h2>
 *
 * <p>The graph is wider than the screen at any sane GUI scale, so dragging pans
 * it. A drag begins with the same button-down as a purchase, so the purchase
 * fires on <em>release</em>, and only if the pointer never travelled far enough
 * to count as a drag. Deciding on press instead would buy a node every time
 * someone grabbed the canvas next to one.</p>
 */
public final class ResonanceTreeTab implements TomeTab {

    // Grid geometry.
    private static final int NODE_WIDTH = 108;
    private static final int NODE_HEIGHT = 34;
    private static final int COLUMN_GAP = 18;
    private static final int ROW_GAP = 12;
    private static final int PADDING = 14;
    private static final int HEADER_HEIGHT = 12;

    /** Pointer travel, in pixels, past which a press is a pan rather than a click. */
    private static final int DRAG_SLOP = 4;

    private static final int COLOR_MAXED = 0xFFFFC44F;
    private static final int COLOR_UNLOCKED = 0xFF6FCF6F;
    private static final int COLOR_AVAILABLE = 0xFFE8E8E8;
    private static final int COLOR_LOCKED = 0xFF5A5A5A;
    private static final int COLOR_TRADEOFF_ON = 0xFF4FC3F7;
    private static final int COLOR_BODY = 0xFF9A9A9A;
    private static final int COLOR_BRANCH = 0xFF8A8A8A;
    private static final int COLOR_NODE_FILL = 0xB0101010;
    private static final int COLOR_EDGE = 0xFF3A3A3A;
    private static final int COLOR_EDGE_MET = 0xFF5F8F5F;

    private final Component title;
    private final TreeLayout.Layout layout;
    private final List<NodeDef> nodes;

    private int scrollX;
    private int scrollY;
    private boolean pressed;
    private double pressX;
    private double pressY;
    private boolean dragging;

    public ResonanceTreeTab(Component title) {
        this.title = title;
        this.nodes = visibleNodes();
        this.layout = TreeLayout.of(nodes);
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
        // The graph is drawn, not built from widgets; see TomeTab's input methods.
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
        SyncSkillTree tree = ClientPacketHandlers.skillTree();
        SyncTeamProgress progress = ClientPacketHandlers.teamProgress();
        if (tree == null || progress == null) {
            graphics.centeredText(font, Component.translatable("screen.orevault.tome.syncing"),
                    area.left() + area.width() / 2, area.top() + area.height() / 2 - font.lineHeight / 2,
                    COLOR_BODY);
            return;
        }

        clampScroll(area);
        Map<String, Integer> tiers = tree.resonanceTiers();
        Set<String> active = Set.copyOf(tree.activeTradeoffs());
        int teamLevel = progress.resonance().level();
        int points = progress.resonance().unspentPoints();

        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        drawBranchLabels(graphics, font, area);
        drawEdges(graphics, area, tiers);
        NodeDef hovered = null;
        for (NodeDef def : nodes) {
            boolean isHovered = drawNode(graphics, font, area, def, tiers, active, teamLevel, points,
                    mouseX, mouseY);
            if (isHovered) {
                hovered = def;
            }
        }
        graphics.disableScissor();

        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font,
                    tooltip(hovered, tiers, active, teamLevel, points), mouseX, mouseY);
        }
    }

    private void drawBranchLabels(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area) {
        for (int column = 0; column < layout.branches().size(); column++) {
            int x = columnX(area, column);
            graphics.text(font, Component.literal(layout.branches().get(column)),
                    x, area.top() + PADDING - scrollY, COLOR_BRANCH);
        }
    }

    /**
     * Prerequisite edges.
     *
     * <p>Drawn as an elbow rather than a straight line: two nodes in different
     * branches are far apart horizontally, and a diagonal across four other
     * nodes is worse than a right angle that leaves the cells alone. Met
     * prerequisites are green so the shape of what you have already bought
     * reads at a glance.</p>
     */
    private void drawEdges(GuiGraphicsExtractor graphics, ScreenRectangle area, Map<String, Integer> tiers) {
        for (NodeDef def : nodes) {
            TreeLayout.Cell to = layout.cell(def.id());
            if (to == null) {
                continue;
            }
            for (Prereq prereq : def.prereqs()) {
                TreeLayout.Cell from = layout.cell(prereq.nodeId());
                if (from == null) {
                    continue;
                }
                boolean met = tiers.getOrDefault(prereq.nodeId(), 0) >= prereq.minTier();
                int color = met ? COLOR_EDGE_MET : COLOR_EDGE;
                int x1 = columnX(area, from.column()) + NODE_WIDTH / 2;
                int y1 = rowY(area, from.row()) + NODE_HEIGHT;
                int x2 = columnX(area, to.column()) + NODE_WIDTH / 2;
                int y2 = rowY(area, to.row());
                int mid = (y1 + y2) / 2;
                graphics.verticalLine(x1, Math.min(y1, mid) - 1, Math.max(y1, mid), color);
                graphics.horizontalLine(Math.min(x1, x2), Math.max(x1, x2), mid, color);
                graphics.verticalLine(x2, Math.min(mid, y2) - 1, Math.max(mid, y2), color);
            }
        }
    }

    /** Draws one node; returns whether the pointer is over it. */
    private boolean drawNode(GuiGraphicsExtractor graphics, Font font, ScreenRectangle area, NodeDef def,
                             Map<String, Integer> tiers, Set<String> active, int teamLevel, int points,
                             int mouseX, int mouseY) {
        TreeLayout.Cell cell = layout.cell(def.id());
        if (cell == null) {
            return false;
        }
        int x = columnX(area, cell.column());
        int y = rowY(area, cell.row());
        if (y + NODE_HEIGHT < area.top() || y > area.bottom()
                || x + NODE_WIDTH < area.left() || x > area.right()) {
            return false;
        }

        int tier = tiers.getOrDefault(def.id(), 0);
        int border = borderColor(def, tiers, active, teamLevel, points);
        graphics.fill(x, y, x + NODE_WIDTH, y + NODE_HEIGHT, COLOR_NODE_FILL);
        graphics.outline(x, y, NODE_WIDTH, NODE_HEIGHT, border);

        Component name = displayName(def);
        graphics.text(font, font.width(name) > NODE_WIDTH - 8
                        ? Component.literal(font.plainSubstrByWidth(name.getString(), NODE_WIDTH - 14) + "…")
                        : name,
                x + 4, y + 4, border);

        Component detail = tier >= def.maxTier()
                ? Component.translatable("screen.orevault.tome.node.maxed", tier)
                : Component.translatable("screen.orevault.tome.node.tier", tier, def.maxTier(),
                        def.costs()[tier], def.levelReqs()[tier]);
        graphics.text(font, detail, x + 4, y + 4 + font.lineHeight + 2,
                tier >= def.maxTier() ? COLOR_MAXED : COLOR_BODY);

        if (def.tradeoff() && tier > 0) {
            boolean on = active.contains(def.id());
            graphics.fill(x + NODE_WIDTH - 9, y + 4, x + NODE_WIDTH - 4, y + 9,
                    on ? COLOR_TRADEOFF_ON : COLOR_LOCKED);
        }
        if (def.isExclusive() && tiers.getOrDefault(def.exclusiveWith(), 0) > 0) {
            graphics.text(font, Component.literal("x"), x + NODE_WIDTH - 10, y + 3, COLOR_LOCKED);
        }

        return mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + NODE_HEIGHT;
    }

    private int borderColor(NodeDef def, Map<String, Integer> tiers, Set<String> active, int teamLevel,
                            int points) {
        int tier = tiers.getOrDefault(def.id(), 0);
        if (tier >= def.maxTier()) {
            return COLOR_MAXED;
        }
        if (def.tradeoff() && tier > 0 && active.contains(def.id())) {
            return COLOR_TRADEOFF_ON;
        }
        if (tier > 0) {
            return COLOR_UNLOCKED;
        }
        return purchasable(def, tiers, teamLevel, points) ? COLOR_AVAILABLE : COLOR_LOCKED;
    }

    /**
     * Whether the next tier looks buyable from here.
     *
     * <p>A deliberate re-implementation of {@code SkillTree.canUnlock} rather
     * than a call to it: the client has a tier map, not a {@code SkillTree}, and
     * building one just to ask would invite treating the answer as authoritative.
     * The server checks again and its answer is the one that counts — this only
     * decides whether a node is drawn bright.</p>
     */
    private boolean purchasable(NodeDef def, Map<String, Integer> tiers, int teamLevel, int points) {
        return lockReason(def, tiers, teamLevel, points) == null;
    }

    /** The lang key explaining why the next tier cannot be bought, or {@code null} if it can. */
    private @Nullable String lockReason(NodeDef def, Map<String, Integer> tiers, int teamLevel, int points) {
        int tier = tiers.getOrDefault(def.id(), 0);
        if (tier >= def.maxTier()) {
            return "screen.orevault.tome.node.locked.maxed";
        }
        for (Prereq prereq : def.prereqs()) {
            if (tiers.getOrDefault(prereq.nodeId(), 0) < prereq.minTier()) {
                return "screen.orevault.tome.node.locked.prereq";
            }
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
                                    int teamLevel, int points) {
        List<Component> lines = new ArrayList<>();
        lines.add(displayName(def).copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable("node.orevault." + def.id() + ".desc")
                .withStyle(ChatFormatting.GRAY));

        int tier = tiers.getOrDefault(def.id(), 0);
        lines.add(Component.translatable("screen.orevault.tome.node.owned", tier, def.maxTier())
                .withStyle(ChatFormatting.DARK_GRAY));
        if (tier < def.maxTier()) {
            lines.add(Component.translatable("screen.orevault.tome.node.next",
                    tier + 1, def.costs()[tier], def.levelReqs()[tier]).withStyle(ChatFormatting.DARK_GRAY));
        }

        String reason = lockReason(def, tiers, teamLevel, points);
        if (reason != null && tier < def.maxTier()) {
            lines.add(Component.translatable(reason).withStyle(ChatFormatting.RED));
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
     * <p>The fallback matters because the node set is mid-rework (#102, #103):
     * a node added to {@code NodeDefs} without a matching lang key should read
     * as its English name, not as {@code node.orevault.whatever}.</p>
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
        scrollY -= (int) Math.round(scrollDeltaY * (NODE_HEIGHT + ROW_GAP) / 2.0);
        scrollX -= (int) Math.round(scrollDeltaX * (NODE_WIDTH + COLUMN_GAP) / 2.0);
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

    private @Nullable NodeDef nodeAt(ScreenRectangle area, int mouseX, int mouseY) {
        for (NodeDef def : nodes) {
            TreeLayout.Cell cell = layout.cell(def.id());
            if (cell == null) {
                continue;
            }
            int x = columnX(area, cell.column());
            int y = rowY(area, cell.row());
            if (mouseX >= x && mouseX < x + NODE_WIDTH && mouseY >= y && mouseY < y + NODE_HEIGHT) {
                return def;
            }
        }
        return null;
    }

    // ----- geometry -----

    private int columnX(ScreenRectangle area, int column) {
        return area.left() + PADDING + column * (NODE_WIDTH + COLUMN_GAP) - scrollX;
    }

    private int rowY(ScreenRectangle area, int row) {
        return area.top() + PADDING + HEADER_HEIGHT + row * (NODE_HEIGHT + ROW_GAP) - scrollY;
    }

    private int contentWidth() {
        return PADDING * 2 + layout.columnCount() * (NODE_WIDTH + COLUMN_GAP) - COLUMN_GAP;
    }

    private int contentHeight() {
        return PADDING * 2 + HEADER_HEIGHT + layout.rowCount() * (NODE_HEIGHT + ROW_GAP) - ROW_GAP;
    }

    /**
     * Keeps the graph on screen.
     *
     * <p>When the graph is smaller than the area the scroll is pinned to 0
     * rather than allowed to go negative, so a short tree sits at the top-left
     * instead of drifting off it.</p>
     */
    private void clampScroll(ScreenRectangle area) {
        scrollX = Math.max(0, Math.min(scrollX, Math.max(0, contentWidth() - area.width())));
        scrollY = Math.max(0, Math.min(scrollY, Math.max(0, contentHeight() - area.height())));
    }
}
