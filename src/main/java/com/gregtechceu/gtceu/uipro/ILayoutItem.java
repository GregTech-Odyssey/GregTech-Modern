package com.gregtechceu.gtceu.uipro;

import dev.vfyjxf.taffy.util.MeasureFunc;
import org.jetbrains.annotations.Nullable;

/**
 * 不是 {@link UIElement} 的控件（LDLib1 原生控件的子类，如 {@code Button}、{@code ScrollerView}）参与 Taffy 布局的方式。
 * <p>
 * 放进 UIElement 的普通 LDLib1 控件都是布局树里的叶子，尺寸固定为控件当前尺寸。实现本接口后改用自己的
 * {@link LayoutStyle}（可以 auto 宽度被拉伸、flexGrow 等），需要时再提供测量函数（尺寸由内容决定，如滚动区高度跟随内容）。
 * 布局结果（位置、尺寸）由父 UIElement 写回控件。样式或测量结果变了调用 {@link UIElement#markLayoutDirty}。
 */
public interface ILayoutItem {

    LayoutStyle getLayoutStyle();

    /** 叶子的测量函数，为 null 时按样式尺寸。 */
    @Nullable
    default MeasureFunc getLayoutMeasure() {
        return null;
    }
}
