package com.gregtechceu.gtceu.uiwidgets.number;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.network.chat.Component;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * 单个数值的设置（优先级、并行数、超频限制……）：一个区块，标题一行，下面一行整宽的 {@link NumberField}。
 * 
 * <pre>
 *  ┌ 优先级 ──────────────────┐
 *  │ [-1] [      0      ] [+1] │
 *  └──────────────────────────┘
 * </pre>
 * 
 * 作为页面时宽 {@link UISizes#CONTENT_WIDTH}；作为机器左侧的展开项时用 {@link #compact} 的窄版。
 */
public final class NumberSettingPage {

    /** 展开项（机器左侧配置项）里的宽度。 */
    public static final int COMPACT_WIDTH = 120;

    private NumberSettingPage() {}

    public static UIElement create(Component title, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, long... steps) {
        return section(UISizes.CONTENT_WIDTH, title, getter, setter, min, max, steps);
    }

    /** 只有输入行、没有标题的窄版：展开项的标题栏已经写明设置的是什么。 */
    public static UIElement compact(LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, long... steps) {
        return UIElement.column(COMPACT_WIDTH).addChild(new NumberField(COMPACT_WIDTH, getter, setter, min, max, steps));
    }

    private static UIElement section(int width, Component title, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, long... steps) {
        var section = UIElement.section(width);
        section.addChildren(TextLine.constant(LayoutStyle.AUTO, title).setColor(UITheme.PANEL_TEXT),
                new NumberField(width - 2 * UITheme.PANEL_PADDING, getter, setter, min, max, steps));
        return section;
    }
}
