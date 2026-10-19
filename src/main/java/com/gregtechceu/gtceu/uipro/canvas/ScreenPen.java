package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.view.PlanarView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.joml.Matrix4f;

/**
 * 画布图层 draw 里的屏幕像素画笔：begin 把世界 pose 换回与画布原点对齐的整数界面像素格，x/y 把世界点换到格上；平移时整体移动不抖。
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenPen {

    private final Matrix4f saved = new Matrix4f();
    private GuiGraphics graphics;
    private Matrix4f matrix;
    private double scale = 1;
    private int baseX, baseY, clipLeft, clipTop, clipRight, clipBottom;

    public ScreenPen begin(GuiGraphics graphics, PlanarView view) {
        float s = view.scale();
        this.graphics = graphics;
        this.scale = s;
        this.baseX = Math.round(view.screenOriginX());
        this.baseY = Math.round(view.screenOriginY());
        this.clipLeft = view.viewportX();
        this.clipTop = view.viewportY();
        this.clipRight = clipLeft + view.viewportWidth();
        this.clipBottom = clipTop + view.viewportHeight();
        matrix = graphics.pose().last().pose();
        saved.set(matrix);
        matrix.scale(1 / s, 1 / s, 1).translate(-baseX, -baseY, 0);
        return this;
    }

    public void end() {
        if (matrix == null) return;
        UIDraw.endLines(graphics);
        matrix.set(saved);
        matrix = null;
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public double pixelsPerUnit() {
        return scale;
    }

    public int x(double worldX) {
        return baseX + (int) Math.round(worldX * scale);
    }

    public int y(double worldY) {
        return baseY + (int) Math.round(worldY * scale);
    }

    public int length(double world) {
        return (int) Math.round(world * scale);
    }

    public int clipLeft() {
        return clipLeft;
    }

    public int clipTop() {
        return clipTop;
    }

    public int clipRight() {
        return clipRight;
    }

    public int clipBottom() {
        return clipBottom;
    }

    public boolean isVisible(int left, int top, int right, int bottom) {
        return right > clipLeft && left < clipRight && bottom > clipTop && top < clipBottom;
    }

    public void line(int x0, int y0, int x1, int y1, int thickness, int color) {
        UIDraw.line(graphics, x0, y0, x1, y1, thickness, color, clipLeft, clipTop, clipRight, clipBottom);
    }

    public void dashedLine(int x0, int y0, int x1, int y1, int thickness, int color, int dashOn, int dashOff, int phase) {
        UIDraw.dashedLine(graphics, x0, y0, x1, y1, thickness, color, clipLeft, clipTop, clipRight, clipBottom, dashOn, dashOff, phase);
    }

    public void flushLines() {
        UIDraw.endLines(graphics);
    }
}
