package com.gregtechceu.gtceu.uipro.canvas;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 画布的一层，按加入顺序从下往上画、命中测试从上往下找（对应 LDLib2 GraphView 的图层）。
 * 连线、分区线这类不可交互的背景用 {@link #of}；节点这类可交互的项用 {@link ItemLayer}。
 * <p>
 * 图层只在客户端存在：在 {@link CanvasView#setScene} 的回调里建（该回调只在客户端执行），
 * 不要在两端都会执行的建界面代码里直接创建——绘制用的 {@link CanvasPainter} 在专用服务器上不存在。
 */
public interface CanvasLayer {

    @OnlyIn(Dist.CLIENT)
    void draw(CanvasPainter painter, @Nullable CanvasItem hovered);

    /** 世界坐标里被命中的项，没有为 null。 */
    @Nullable
    default CanvasItem pick(float x, float y) {
        return null;
    }

    /** 本层内容的范围，没有内容为 null。"适应全部"和缩略图按所有层的并集算。 */
    @Nullable
    default CanvasRect bounds() {
        return null;
    }

    /** 本帧可见且选中的项的范围（画布据此在屏幕空间画统一选中框），默认没有。 */
    @OnlyIn(Dist.CLIENT)
    default void collectSelected(List<CanvasRect> out) {}

    /** 缩略图里的样子，默认不画。 */
    @OnlyIn(Dist.CLIENT)
    default void drawMinimap(CanvasPainter painter) {}

    /** 只画、不交互的一层（参数是画笔）；需要知道当前悬停项时用 {@link #of(Drawer)}。 */
    @OnlyIn(Dist.CLIENT)
    static CanvasLayer of(Consumer<CanvasPainter> drawer) {
        return of((painter, hovered) -> drawer.accept(painter));
    }

    /** 只画、不交互、需要知道当前悬停项（如高亮悬停节点的连线）的一层。 */
    @OnlyIn(Dist.CLIENT)
    static CanvasLayer of(Drawer drawer) {
        return new CanvasLayer() {

            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(CanvasPainter painter, @Nullable CanvasItem hovered) {
                drawer.draw(painter, hovered);
            }
        };
    }

    @FunctionalInterface
    interface Drawer {

        void draw(CanvasPainter painter, @Nullable CanvasItem hovered);
    }
}
