package com.orevault.orevault.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;

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
}
