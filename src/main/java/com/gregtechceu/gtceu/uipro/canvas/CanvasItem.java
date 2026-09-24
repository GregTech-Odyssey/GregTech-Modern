package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * 画布里一个可交互的项（节点、标记……），对应 LDLib2 里放进 {@code GraphView} 内容根的元素。
 * <p>
 * 与界面控件不同，项不是 LDLib 控件，不进控件树、不参与同步：画布只有一个控件，项只是它在客户端画出来的内容。
 * 这样几百个节点也只有一次命中测试和一批绘制，两端控件树也不会因为内容多少而变化。
 * 需要改服务端数据的交互（点击解锁等）由使用方在 {@link CanvasView#setOnItemClick} 里走自己的请求通道。
 * <p>
 * 绘制分三遍（见 {@link ItemLayer}）：先所有项的 {@link #drawShape}（色块、边框，攒成一批），再所有项的 {@link #drawContent}
 * （图标、文字），最后所有项的 {@link #drawOverlay}（盖在图标上面的蒙层，抬高后攒成一批）；
 * {@link CanvasLod#BLOCK} 时只画 {@link #blockColor()} 的一块纯色，三个方法都不调用。
 */
public interface CanvasItem {

    /** 世界坐标里的范围：用来剔除、命中测试、适应视图、缩略图。 */
    CanvasRect bounds();

    /** 命中测试（世界坐标），默认按 {@link #bounds()}。 */
    default boolean hitTest(float x, float y) {
        return bounds().contains(x, y);
    }

    /** 形状：色块、边框、线条（用画笔攒批的那些）。 */
    @OnlyIn(Dist.CLIENT)
    void drawShape(CanvasPainter painter, boolean hovered);

    /** 内容：图标、文字（自己提交绘制的那些），在所有项的形状之后画。{@link CanvasLod#SIMPLIFIED} 时也会调用，按需自行省略。 */
    @OnlyIn(Dist.CLIENT)
    default void drawContent(CanvasPainter painter, boolean hovered) {}

    /**
     * 盖在图标之上的纯色蒙层（如"未解锁"变灰、悬停提亮），只能用画笔的攒批方法（不要 flush、不要自己画图标）：
     * 所有项的蒙层抬高到图标之上后一次提交。
     */
    @OnlyIn(Dist.CLIENT)
    default void drawOverlay(CanvasPainter painter, boolean hovered) {}

    /** {@link CanvasLod#BLOCK} 与缩略图里代表本项的纯色。 */
    default int blockColor() {
        return UITheme.CANVAS_ITEM_BLOCK;
    }

    /** 是否选中：选中项在屏幕空间画统一选中框（与物品槽的选中框相同）。客户端判定，属于界面状态。 */
    default boolean isSelected() {
        return false;
    }

    /** 悬停提示。 */
    default List<Component> tooltip() {
        return Collections.emptyList();
    }

    /** 鼠标停在本项上时交给 EMI 的物品（查配方、查用途、收藏），没有时为 null。 */
    @Nullable
    default Object ingredient() {
        return null;
    }
}
