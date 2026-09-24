package com.gregtechceu.gtceu.uipro.canvas;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.Nullable;

/**
 * 画布的画笔：所有坐标都是<b>世界坐标</b>，画布已把 pose 平移、缩放好（1 个世界单位 = {@link #scale()} 个界面像素）。
 * <p>
 * 纯色块、线条按浮点坐标攒进同一批顶点（{@link #fill} 等），数百条连线也只是一次绘制；
 * 画图标、文字这类自己提交绘制的内容之前先 {@link #flush()}，否则攒着的色块会画在它们上面。
 * {@link ItemLayer} 已经按"先所有形状、flush、再所有内容"两遍绘制，项里一般不用自己 flush。
 * <p>
 * 线宽有两种写法：世界单位（随缩放变粗变细），或 {@link #px} 换算的屏幕像素（任何缩放下都一样粗）。
 * 线条类方法会把宽度至少保持 1 个屏幕像素，缩得再小也不会消失。
 */
@OnlyIn(Dist.CLIENT)
public final class CanvasPainter {

    private GuiGraphics graphics;
    private float scale = 1;
    private CanvasLod lod = CanvasLod.FULL;
    private CanvasRect visible = CanvasRect.of(0, 0, 0, 0);
    private float mouseX = Float.NaN, mouseY = Float.NaN;
    @Nullable
    private CanvasItem hovered;

    /** 画布持有一个画笔、每帧 {@link #begin} 重设一次（不每帧新建）。 */
    CanvasPainter() {}

    CanvasPainter begin(GuiGraphics graphics, float scale, CanvasLod lod, CanvasRect visible, float mouseX, float mouseY, @Nullable CanvasItem hovered) {
        this.graphics = graphics;
        this.scale = scale;
        this.lod = lod;
        this.visible = visible;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hovered = hovered;
        return this;
    }

    /** 底层 GuiGraphics（pose 已是世界坐标）；直接用它画东西之前先 {@link #flush()}。 */
    public GuiGraphics graphics() {
        return graphics;
    }

    /** 当前缩放：1 个世界单位占多少界面像素。 */
    public float scale() {
        return scale;
    }

    /** {@code screenPixels} 个界面像素换算成的世界长度。 */
    public float px(float screenPixels) {
        return screenPixels / scale;
    }

    public CanvasLod lod() {
        return lod;
    }

    /** 视口里能看到的世界范围（已含一点余量），用它剔除看不见的内容。 */
    public CanvasRect visible() {
        return visible;
    }

    public boolean isVisible(CanvasRect rect) {
        return visible.intersects(rect);
    }

    public boolean isVisible(float x, float y, float width, float height) {
        return x < visible.right() && x + width > visible.x() && y < visible.bottom() && y + height > visible.y();
    }

    /** 鼠标的世界坐标（鼠标不在视口里时为 NaN）。 */
    public float mouseX() {
        return mouseX;
    }

    public float mouseY() {
        return mouseY;
    }

    /** 当前悬停的项（任何层里的），没有为 null。项可以据此高亮与悬停项相关的内容（如被依赖的节点）。 */
    @Nullable
    public CanvasItem hovered() {
        return hovered;
    }

    // ==================== 纯色（攒批） ====================

    /** 实心矩形，[x0, x1) × [y0, y1)，世界坐标。 */
    public void fill(float x0, float y0, float x1, float y1, int color) {
        if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();
        int a = color >>> 24, r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        buffer.vertex(matrix, x0, y0, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x0, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y0, 0).color(r, g, b, a).endVertex();
    }

    public void fill(CanvasRect rect, int color) {
        fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }

    /** 矩形边框，向内画 {@code thickness}（世界单位，至少 1 个屏幕像素）。 */
    public void outline(CanvasRect rect, float thickness, int color) {
        float t = Math.min(atLeastPixel(thickness), Math.min(rect.width(), rect.height()) / 2);
        float l = rect.x(), top = rect.y(), r = rect.right(), b = rect.bottom();
        fill(l, top, r, top + t, color);
        fill(l, b - t, r, b, color);
        fill(l, top + t, l + t, b - t, color);
        fill(r - t, top + t, r, b - t, color);
    }

    /**
     * 1 像素斜面框（与 {@code OreSprites.Bevel} 同一画法，按屏幕像素）：左上 {@code topLeft}、右下 {@code bottomRight}、中间 {@code fill}。
     * 画布里的"格子"用它，放大缩小时边框始终是 1 个屏幕像素。
     */
    public void bevel(CanvasRect rect, int topLeft, int bottomRight, int fillColor) {
        float p = Math.min(px(1), Math.min(rect.width(), rect.height()) / 2);
        float l = rect.x(), t = rect.y(), r = rect.right(), b = rect.bottom();
        fill(l, t, r, b, fillColor);
        fill(l, t, r - p, t + p, topLeft);
        fill(l, t + p, l + p, b - p, topLeft);
        fill(l + p, b - p, r, b, bottomRight);
        fill(r - p, t + p, r, b - p, bottomRight);
    }

    /** 横线，以 {@code y} 为中线、宽 {@code thickness}（世界单位，至少 1 个屏幕像素）。 */
    public void hLine(float x0, float x1, float y, float thickness, int color) {
        float t = atLeastPixel(thickness);
        fill(Math.min(x0, x1) - t / 2, y - t / 2, Math.max(x0, x1) + t / 2, y + t / 2, color);
    }

    /** 竖线，以 {@code x} 为中线。 */
    public void vLine(float x, float y0, float y1, float thickness, int color) {
        float t = atLeastPixel(thickness);
        fill(x - t / 2, Math.min(y0, y1) - t / 2, x + t / 2, Math.max(y0, y1) + t / 2, color);
    }

    /** 竖直虚线：每段 {@code dash}、间隔 {@code gap}（都是世界单位）。 */
    public void dashedVLine(float x, float y0, float y1, float thickness, float dash, float gap, int color) {
        float t = atLeastPixel(thickness);
        float top = Math.max(Math.min(y0, y1), visible.y() - dash), bottom = Math.min(Math.max(y0, y1), visible.bottom());
        float period = Math.max(dash + gap, px(2));
        // 从 y0 所在的相位开始，保证平移时虚线不"滑动"
        float start = Math.min(y0, y1) + (float) Math.floor((top - Math.min(y0, y1)) / period) * period;
        for (float y = start; y < bottom; y += period) {
            fill(x - t / 2, y, x + t / 2, Math.min(y + dash, bottom), color);
        }
    }

    /**
     * 沿路径画线：{@code points} 为 x0, y0, x1, y1, ...，只支持横平竖直的线段（见 {@link CanvasRoute}）；
     * 线段两端各延长半个线宽，拐角处正好补齐。
     */
    public void path(float[] points, float thickness, int color) {
        for (int i = 0; i + 3 < points.length; i += 2) {
            float x0 = points[i], y0 = points[i + 1], x1 = points[i + 2], y1 = points[i + 3];
            if (y0 == y1) hLine(x0, x1, y0, thickness, color);
            else vLine(x0, y0, y1, thickness, color);
        }
    }

    /** 线宽至少 1 个屏幕像素。 */
    public float atLeastPixel(float thickness) {
        return Math.max(thickness, px(1));
    }

    /** 把攒着的色块画出去。画图标、文字前调用。 */
    public void flush() {
        graphics.flush();
    }

    // ==================== 图标、文字（立即绘制） ====================

    /** 贴图，世界坐标；先 flush 攒着的色块。 */
    public void texture(IGuiTexture texture, float x, float y, float width, float height) {
        flush();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(width / 16f, height / 16f, 1);
        texture.draw(graphics, 0, 0, 0, 0, 16, 16);
        pose.popPose();
    }
}
