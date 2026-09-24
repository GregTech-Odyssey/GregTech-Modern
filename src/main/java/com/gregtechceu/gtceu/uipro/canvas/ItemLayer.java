package com.gregtechceu.gtceu.uipro.canvas;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 由 {@link CanvasItem} 组成的一层：只画与视口相交的项；命中测试从后往前找（后加的在上面）；
 * 绘制分"所有形状 → 所有内容 → 所有蒙层"三遍，每遍的色块只提交一次；{@link CanvasLod#BLOCK} 时每项一块纯色。
 */
public class ItemLayer<T extends CanvasItem> implements CanvasLayer {

    private final List<T> items = new ArrayList<>();
    @Nullable
    private CanvasRect bounds;
    /// 本帧与视口相交的项，三遍绘制和选中框共用，避免重复判定
    private final List<T> visibleItems = new ArrayList<>();

    public ItemLayer<T> add(T item) {
        items.add(item);
        bounds = CanvasRect.union(bounds, item.bounds());
        return this;
    }

    public ItemLayer<T> addAll(Iterable<? extends T> items) {
        for (var item : items) add(item);
        return this;
    }

    /** 清空（换一批内容时用，如切换科技树）。 */
    public void clear() {
        items.clear();
        visibleItems.clear();
        bounds = null;
    }

    public List<T> items() {
        return items;
    }

    /// 蒙层画在物品图标（z 约 150~160）之上
    private static final float OVERLAY_Z = 200;

    @Override
    @OnlyIn(Dist.CLIENT)
    public void draw(CanvasPainter painter, @Nullable CanvasItem hovered) {
        visibleItems.clear();
        for (var item : items) {
            if (painter.isVisible(item.bounds())) visibleItems.add(item);
        }
        if (painter.lod() == CanvasLod.BLOCK) {
            for (var item : visibleItems) painter.fill(item.bounds(), item.blockColor());
            return;
        }
        for (var item : visibleItems) item.drawShape(painter, item == hovered);
        painter.flush();
        for (var item : visibleItems) item.drawContent(painter, item == hovered);
        painter.flush();
        var pose = painter.graphics().pose();
        pose.pushPose();
        pose.translate(0, 0, OVERLAY_Z);
        for (var item : visibleItems) item.drawOverlay(painter, item == hovered);
        painter.flush();
        pose.popPose();
    }

    @Override
    @Nullable
    public CanvasItem pick(float x, float y) {
        for (int i = items.size() - 1; i >= 0; i--) {
            var item = items.get(i);
            if (item.hitTest(x, y)) return item;
        }
        return null;
    }

    @Override
    @Nullable
    public CanvasRect bounds() {
        return bounds;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawMinimap(CanvasPainter painter) {
        for (var item : items) painter.fill(item.bounds(), item.blockColor());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void collectSelected(List<CanvasRect> out) {
        for (var item : visibleItems) {
            if (item.isSelected()) out.add(item.bounds());
        }
    }
}
