package com.gregtechceu.gtceu.uipro;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

/**
 * 承载 {@link UIElement} 树的外壳（机器窗口等）。LDLib2 的布局变化会一路传到根，这里对应这一环：
 * 一棵 UIElement 树的根（父控件不是 UIElement）尺寸变了，会通知最近的外壳重新排布，
 * 窗口因此随页面内容动态变高 / 变矮，而不是只在切页时量一次。
 * <p>
 * 外壳只能改尺寸和位置，不能增删控件：尺寸可能只在客户端变化（文字在客户端测量），两端控件树必须保持一致。
 */
public interface ILayoutHost {

    /** {@code root} 是一棵 UIElement 树的根，尺寸刚刚变化。 */
    void onContentResized(Widget root);

    /** 玩家开始拖拽缩放（客户端）：此后窗口不再重新居中，屏幕上的位置保持不动。 */
    default void beginInteractiveResize() {}

    /** 拖拽缩放结束（客户端）：窗口用动画移回居中位置。 */
    default void endInteractiveResize() {}

    /**
     * 拖拽缩放时窗口在该方向上还能再长多少像素（客户端）：整个界面不超过屏幕的
     * {@link com.gregtechceu.gtceu.uipro.styletemplate.UISizes#MAX_WINDOW_SCREEN_RATIO}。已经超过时为 0 或负数。
     */
    default int resizeAllowance(boolean vertical) {
        return Integer.MAX_VALUE;
    }

    /** 从 {@code widget} 往上找最近的外壳。 */
    static ILayoutHost of(Widget widget) {
        for (var ancestor = widget.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof ILayoutHost host) return host;
        }
        return null;
    }
}
