package com.orevault.orevault.client.screen;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/**
 * A Tome tab whose content has not been built yet ([34]).
 *
 * <p>It names the ticket that will fill it. A blank panel and an unfinished
 * panel look identical, and the difference matters most to whoever opens the
 * Tome during a playtest and has to decide whether they are looking at a bug.
 * The three real tabs — Resonance ([35]), Animus ([36]), Ore Memory ([37]) —
 * each replace one of these.</p>
 */
public final class PlaceholderTab implements TomeTab {

    private static final int TITLE_COLOR = 0xFFE0E0E0;
    private static final int BODY_COLOR = 0xFF9A9A9A;
    private static final int LINE_SPACING = 4;

    private final Component title;
    private final Component note;

    public PlaceholderTab(Component title, Component note) {
        this.title = title;
        this.note = note;
    }

    @Override
    public Component getTabTitle() {
        return title;
    }

    @Override
    public Component getTabExtraNarration() {
        return note;
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> childrenConsumer) {
        // No widgets: this tab is two lines of text.
    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {
        // Nothing to lay out. drawContent centres itself in whatever it is given.
    }

    @Override
    public void drawContent(GuiGraphicsExtractor graphics, ScreenRectangle area, int mouseX, int mouseY,
                            float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = area.left() + area.width() / 2;
        int centerY = area.top() + area.height() / 2;
        int titleY = centerY - font.lineHeight - LINE_SPACING / 2;
        graphics.centeredText(font, title, centerX, titleY, TITLE_COLOR);
        graphics.centeredText(font, note, centerX, titleY + font.lineHeight + LINE_SPACING, BODY_COLOR);
    }
}
