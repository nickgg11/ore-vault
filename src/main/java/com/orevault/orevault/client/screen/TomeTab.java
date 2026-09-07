package com.orevault.orevault.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * One page of the Tome (§8).
 *
 * <p>Vanilla's {@link Tab} only knows how to hand out {@code AbstractWidget}s,
 * which is enough for the settings-style tabs it was written for and not enough
 * here: two of the three pages are a node graph drawn with lines and sprites
 * rather than a column of buttons. So this adds the one thing missing — a draw
 * call for the tab's own body — and leaves widget handling to the vanilla
 * interface underneath.</p>
 *
 * <p>{@link #drawContent} is called by {@link TomeScreen} for the selected tab
 * only, after the screen has drawn its own header and separators. The rectangle
 * handed in is the content area below the header, already excluding the tab bar,
 * so a tab never needs to know the screen's layout constants.</p>
 */
public interface TomeTab extends Tab {

    /**
     * Draws this tab's body.
     *
     * @param area the content region, below the tab bar and the progress header
     */
    void drawContent(GuiGraphicsExtractor graphics, ScreenRectangle area, int mouseX, int mouseY, float partialTick);

    // ----- input -----

    /**
     * Mouse handling, forwarded by {@link TomeScreen} only for events inside
     * {@code area} and only to the selected tab.
     *
     * <p>These exist for the same reason {@link #drawContent} does. A screen
     * routes input to its widgets, and a node graph is not made of widgets — the
     * clickable regions are computed from the layout every frame, so there is
     * nothing to register. Returning {@code true} consumes the event; the
     * defaults consume nothing, which is what a tab made only of text wants.</p>
     */
    default boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, ScreenRectangle area) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event, ScreenRectangle area) {
        return false;
    }

    default boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY, ScreenRectangle area) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY,
                                  ScreenRectangle area) {
        return false;
    }
}
