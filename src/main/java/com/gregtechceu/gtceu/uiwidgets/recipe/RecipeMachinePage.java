package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import dev.vfyjxf.taffy.style.AlignContent;

/**
 * 单方块配方机器（普通机器、发电机、无能量机器）主页的外框：宽至少 {@link UISizes#CONTENT_WIDTH}、
 * 高至少 {@link UISizes#RECIPE_MACHINE_PAGE_HEIGHT}，内容更大时才撑高，不同机器的窗口因此一样大。
 * <p>
 * 主体（配方槽位区）在上方剩余空间里居中；附在底部的元素（充电槽、状态行）贴着页面底边、紧挨玩家背包，不随高度浮动。
 */
public final class RecipeMachinePage {

    private RecipeMachinePage() {}

    /** {@code main} 居中，{@code bottom} 依次排在页面底部。 */
    public static UIElement page(Widget main, Widget... bottom) {
        var center = new UIElement().layout(l -> l.column().flexGrow(1).alignCenter().justifyContent(AlignContent.CENTER));
        center.addChild(main);
        var page = new UIElement().layout(l -> l.column().minWidth(UISizes.CONTENT_WIDTH).minHeight(UISizes.RECIPE_MACHINE_PAGE_HEIGHT)
                .gapAll(UISizes.SECTION_GAP).alignCenter());
        page.addChild(center);
        for (var widget : bottom) page.addChild(widget);
        return page;
    }
}
