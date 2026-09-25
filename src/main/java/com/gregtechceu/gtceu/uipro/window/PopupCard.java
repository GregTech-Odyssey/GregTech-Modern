package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.IShiftClickPriority;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ItemTitle;
import com.gregtechceu.gtceu.uipro.elements.Label;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

/**
 * 卡片：Ore 窗口外框，第一行标题与 {@code [×]}，下面是高度随内容的滚动区。机器窗口右侧的弹出面板（{@link PopupHost}）
 * 和浮在内容上的卡片（{@link CardHost}）都是它，外观一致。
 * <p>
 * 宽度随内容（flexbox 自适应）：内容至少一个 9 槽区块宽（{@link UISizes#POPUP_CONTENT_WIDTH}），滚动区宽度跟随内容，
 * 放得下时不留滚动条的位置，要滚动时向右加宽；标题行由交叉轴拉伸跟到同宽。高度超过上限时滚动。
 */
public class PopupCard extends UIElement implements IShiftClickPriority {

    /// 标题行、内边距、间距占掉的高度，卡片高度上限减去它就是滚动区的高度上限
    private static final int CHROME = UISizes.POPUP_PADDING + UISizes.CONTROL_HEIGHT + UISizes.SECTION_GAP + UISizes.POPUP_PADDING_BOTTOM;

    private final ScrollerView scroller;

    /**
     * @param scrollerId 滚动区的固定 id（锁定的高度按它保存）
     * @param popup      标题与内容
     * @param maxHeight  卡片高度上限
     * @param close      点 {@code [×]} 时（客户端）
     */
    public PopupCard(String scrollerId, Popup popup, int maxHeight, Runnable close) {
        layout(l -> l.column().gapAll(UISizes.SECTION_GAP).paddingAll(UISizes.POPUP_PADDING).paddingBottom(UISizes.POPUP_PADDING_BOTTOM));
        setBackground(UITheme.WINDOW);

        var closeButton = Button.glyph("×").setOnClientClick(close);
        closeButton.setHoverTooltips(MachineWindow.POPUP_CLOSE);
        var titleRow = UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(popupTitle(popup), UIElement.flexSpacer(), closeButton);

        // 内容至少一个 9 槽区块宽（按 getContentWidth 定宽的页面照旧），里面的滚动区被拖宽时跟着变宽
        var content = new UIElement().layout(l -> l.column().widthAuto().minWidth(UISizes.POPUP_CONTENT_WIDTH).gapAll(UISizes.SECTION_GAP));
        popup.content().accept(content);
        // 初始尺寸取最小，宽高都跟随内容（见 ScrollerView 对滚动范围的处理）
        scroller = new ScrollerView(scrollerId, UISizes.POPUP_CONTENT_WIDTH, UISizes.SLOT).adaptiveWidth();
        scroller.addScrollViewChild(content);
        scroller.adaptiveHeight(contentLimit(maxHeight));
        addChildren(titleRow, scroller);
    }

    private static Widget popupTitle(Popup popup) {
        if (popup.icon() == null) return Label.of(popup.title(), UISizes.POPUP_CONTENT_WIDTH - UISizes.ICON_BUTTON - UISizes.GAP);
        return ItemTitle.of(popup.icon(), popup.title()).layout(l -> l.flex(1));
    }

    private static int contentLimit(int maxHeight) {
        return Math.max(UISizes.SLOT, maxHeight - CHROME);
    }

    /** 卡片是玩家正在操作的窗口：Shift+点击时卡片里的槽优先接收。 */
    @Override
    public int getShiftClickPriority() {
        return IShiftClickPriority.POPUP;
    }

    /** 滚动区能否拖拽缩放（默认能）；不能时也不套用锁定的高度，高度只跟随内容与上限。 */
    public PopupCard setResizable(boolean resizable) {
        scroller.setResizable(resizable);
        return this;
    }

    /** 高度上限变了（屏幕尺寸、所在区域尺寸变化）：更新滚动区高度上限（内容放得下就不滚动）。 */
    public void setMaxHeight(int maxHeight) {
        scroller.adaptiveHeight(contentLimit(maxHeight));
    }
}
