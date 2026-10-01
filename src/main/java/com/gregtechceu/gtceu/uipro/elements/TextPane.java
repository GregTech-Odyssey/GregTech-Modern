package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

/**
 * 装着一段 {@link RichText} 的滚动区，三种底：只读状态（{@link #status}）、可点击条目（{@link #panel}）、机器显示屏（{@link #screen}）。
 */
public final class TextPane {

    private TextPane() {}

    public static ScrollerView status(String id, int width, int height, RichText text) {
        return create(id, width, height, UITheme.STATUS_PANEL, text);
    }

    public static ScrollerView panel(String id, int width, int height, RichText text) {
        return create(id, width, height, UITheme.PANEL, text);
    }

    public static ScrollerView screen(String id, int width, int height, RichText text) {
        return create(id, width, height, UITheme.DISPLAY_SCREEN, text.darkBackground())
                .setWatermark(UITheme.SCREEN_LOGO, UISizes.LOGO_WIDTH, UISizes.LOGO_HEIGHT);
    }

    private static ScrollerView create(String id, int width, int height, IGuiTexture background, RichText text) {
        var scroller = new ScrollerView(id, width, height).contentLayout(l -> l.paddingAll(UISizes.PANEL_PADDING));
        scroller.setBackground(background);
        return scroller.addScrollViewChild(text);
    }
}
