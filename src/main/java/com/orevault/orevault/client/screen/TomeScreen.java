package com.orevault.orevault.client.screen;

import org.jspecify.annotations.Nullable;

import com.orevault.orevault.client.ClientPacketHandlers;
import com.orevault.orevault.network.ModNetwork.SyncTeamProgress;
import com.orevault.orevault.network.ModNetwork.TreeProgress;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Tome of the Deep Seam's screen: a three-tab shell with a progress header
 * (§8, [34]).
 *
 * <h2>What this class owns and what it does not</h2>
 *
 * <p>It owns the frame — the tab bar, the header that reports both pools, and
 * the content rectangle the selected tab draws into. It owns none of the three
 * pages: Resonance ([35]), Animus ([36]) and Ore Memory ([37]) each arrive as
 * their own {@link TomeTab} and swap in for a {@link PlaceholderTab} here. The
 * split is deliberate, because the node graph and the stats panels have nothing
 * in common beyond the rectangle they are handed.</p>
 *
 * <h2>The header draws what it was told, or says it was told nothing</h2>
 *
 * <p>Everything in the header comes from {@link ClientPacketHandlers}, which
 * holds the last {@code SyncTeamProgress} the server pushed. Before the first
 * one lands it holds {@code null}, and this screen says so rather than drawing
 * zeroes: a team with 40,000 Resonance flashing "Level 0" for a frame reads as
 * lost progress, which is the one thing a progression UI must never imply. The
 * server pushes on login and on every gain, so the gap is short but real.</p>
 *
 * <p>Not a pause screen. The pools move while the Tome is open — that is rather
 * the point of watching the bar — and pausing a singleplayer world would freeze
 * the mining that feeds it.</p>
 */
public final class TomeScreen extends Screen {

    // Header geometry. The tab bar's own height comes from vanilla.
    private static final int HEADER_HEIGHT = 40;
    private static final int MARGIN = 12;
    private static final int ROW_SPACING = 3;
    private static final int BAR_WIDTH = 130;
    private static final int BAR_HEIGHT = 9;
    private static final int PERCENT_GAP = 6;
    private static final int PERCENT_COLUMN = 26;
    private static final int LEVEL_COLUMN = 92;
    private static final int POINTS_COLUMN = 158;

    private static final int SEPARATOR_COLOR = 0xFF1A1A1A;
    private static final int HEADER_BACKDROP = 0x66000000;
    private static final int BAR_TRACK_COLOR = 0xFF101010;
    private static final int BAR_BORDER_COLOR = 0xFF000000;
    private static final int LABEL_COLOR = 0xFFE0E0E0;
    private static final int VALUE_COLOR = 0xFFB9B9B9;
    private static final int MUTED_COLOR = 0xFF7A7A7A;

    /** Cyan, matching the Resonance orb's tint of the vanilla XP sprite. */
    private static final int RESONANCE_COLOR = 0xFF4FC3F7;
    /** Red, reserved for Animus so the two rows never have to be read by position. */
    private static final int ANIMUS_COLOR = 0xFFE5533D;

    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private @Nullable TabNavigationBar tabNavigationBar;

    public TomeScreen() {
        super(Component.translatable("screen.orevault.tome.title"));
    }

    // ----- layout -----

    @Override
    protected void init() {
        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(
                        new ResonanceTreeTab(
                                Component.translatable("screen.orevault.tome.tab.resonance")),
                        new PlaceholderTab(
                                Component.translatable("screen.orevault.tome.tab.animus"),
                                Component.translatable("screen.orevault.tome.pending.animus")),
                        new PlaceholderTab(
                                Component.translatable("screen.orevault.tome.tab.ore_memory"),
                                Component.translatable("screen.orevault.tome.pending.ore_memory")))
                .build();
        this.addRenderableWidget(this.tabNavigationBar);
        this.tabNavigationBar.selectTab(0, false);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (this.tabNavigationBar == null) {
            return;
        }
        this.tabNavigationBar.updateWidth(this.width);
        this.tabManager.setTabArea(contentArea());
    }

    /** The region below the tab bar and the header, which a tab draws into. */
    private ScreenRectangle contentArea() {
        int top = contentTop();
        return new ScreenRectangle(0, top, this.width, Math.max(0, this.height - top));
    }

    private int headerTop() {
        return this.tabNavigationBar == null ? 0 : this.tabNavigationBar.getRectangle().bottom();
    }

    private int contentTop() {
        return headerTop() + HEADER_HEIGHT;
    }

    // ----- drawing -----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawHeader(graphics, mouseX, mouseY);
        if (this.tabManager.getCurrentTab() instanceof TomeTab tab) {
            tab.drawContent(graphics, contentArea(), mouseX, mouseY, partialTick);
        }
    }

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int top = headerTop();
        int bottom = top + HEADER_HEIGHT;
        graphics.fill(0, top, this.width, bottom, HEADER_BACKDROP);
        graphics.horizontalLine(0, this.width, bottom - 1, SEPARATOR_COLOR);

        SyncTeamProgress progress = ClientPacketHandlers.teamProgress();
        if (progress == null) {
            graphics.centeredText(this.font, Component.translatable("screen.orevault.tome.syncing"),
                    this.width / 2, top + (HEADER_HEIGHT - this.font.lineHeight) / 2, MUTED_COLOR);
            return;
        }

        int rowHeight = this.font.lineHeight + ROW_SPACING;
        int firstRowY = top + (HEADER_HEIGHT - (rowHeight * 2 - ROW_SPACING)) / 2;
        drawTreeRow(graphics, firstRowY, "screen.orevault.tome.header.resonance",
                progress.resonance(), RESONANCE_COLOR, mouseX, mouseY);
        drawTreeRow(graphics, firstRowY + rowHeight, "screen.orevault.tome.header.animus",
                progress.animus(), ANIMUS_COLOR, mouseX, mouseY);
    }

    /**
     * One tree's line: name, level, unspent points, and a bar with its percentage.
     *
     * <p>The bar is anchored to the right edge rather than to a fixed x, so the
     * header holds its shape on the very wide windows a GUI scale of 1 produces
     * as well as on the narrow ones scale 4 produces.</p>
     */
    private void drawTreeRow(GuiGraphicsExtractor graphics, int y, String labelKey, TreeProgress tree, int color,
                             int mouseX, int mouseY) {
        graphics.text(this.font, Component.translatable(labelKey), MARGIN, y, color);
        graphics.text(this.font, Component.translatable("screen.orevault.tome.header.level", tree.level()),
                MARGIN + LEVEL_COLUMN, y, LABEL_COLOR);
        graphics.text(this.font, Component.translatable("screen.orevault.tome.header.points", tree.unspentPoints()),
                MARGIN + POINTS_COLUMN, y, tree.unspentPoints() > 0 ? LABEL_COLOR : MUTED_COLOR);

        int percent = Math.round(clampProgress(tree.progressToNext()) * 100.0F);
        int barRight = this.width - MARGIN - PERCENT_COLUMN - PERCENT_GAP;
        int barLeft = barRight - BAR_WIDTH;
        int barTop = y - 1;

        graphics.fill(barLeft, barTop, barRight, barTop + BAR_HEIGHT, BAR_TRACK_COLOR);
        int filled = Math.round((BAR_WIDTH - 2) * clampProgress(tree.progressToNext()));
        if (filled > 0) {
            graphics.fill(barLeft + 1, barTop + 1, barLeft + 1 + filled, barTop + BAR_HEIGHT - 1, color);
        }
        graphics.outline(barLeft, barTop, BAR_WIDTH, BAR_HEIGHT, BAR_BORDER_COLOR);

        graphics.text(this.font, Component.translatable("screen.orevault.tome.header.percent", percent),
                barRight + PERCENT_GAP, y, VALUE_COLOR);

        // The pool itself is the least glanceable number of the four, so it is a
        // tooltip on the bar rather than a fifth column competing for width.
        boolean hovered = mouseX >= barLeft && mouseX < barRight
                && mouseY >= barTop && mouseY < barTop + BAR_HEIGHT;
        if (hovered) {
            graphics.setTooltipForNextFrame(this.font,
                    Component.translatable("screen.orevault.tome.header.pool", tree.pool()),
                    mouseX, mouseY);
        }
    }

    private static float clampProgress(float progress) {
        return Math.max(0.0F, Math.min(1.0F, progress));
    }

    // ----- input -----

    /*
     * Mouse events reach the selected tab only when they land inside the content
     * area, so the tab bar keeps its own clicks. Everything falls through to
     * super when the tab does not consume it, which is what keeps Escape, the
     * tab buttons and keyboard navigation working.
     */

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (forwardable(event.x(), event.y()) instanceof TomeTab tab
                && tab.mouseClicked(event, doubleClick, contentArea())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // Not gated on position: a drag that began in the content area has to be
        // told it ended even if the pointer left, or the tab stays stuck to the
        // cursor.
        if (this.tabManager.getCurrentTab() instanceof TomeTab tab
                && tab.mouseReleased(event, contentArea())) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.tabManager.getCurrentTab() instanceof TomeTab tab
                && tab.mouseDragged(event, dragX, dragY, contentArea())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (forwardable(mouseX, mouseY) instanceof TomeTab tab
                && tab.mouseScrolled(mouseX, mouseY, scrollX, scrollY, contentArea())) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** The current tab, but only if the given point is inside the content area. */
    private @Nullable Object forwardable(double mouseX, double mouseY) {
        return contentArea().containsPoint((int) mouseX, (int) mouseY)
                ? this.tabManager.getCurrentTab()
                : null;
    }

    // ----- behaviour -----

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
