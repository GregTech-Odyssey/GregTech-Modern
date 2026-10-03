package com.gregtechceu.gtceu.uipro.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 像素直线：先裁到视口，再从可见区间起按 Bresenham 推进，把同一行（列）的像素合并成一个色块。
 */
@OnlyIn(Dist.CLIENT)
final class PixelLines {

    private static final float[] RANGE = new float[2];

    private PixelLines() {}

    static void draw(GuiGraphics graphics, int x0, int y0, int x1, int y1, int thickness, int color,
                     int clipL, int clipT, int clipR, int clipB) {
        if (thickness <= 0 || clipR <= clipL || clipB <= clipT) return;
        int pad = thickness + 1;
        if (!UISegments.clip(x0, y0, x1, y1, clipL - pad, clipT - pad, clipR + pad, clipB + pad, RANGE)) return;
        float t0 = RANGE[0], t1 = RANGE[1];
        var consumer = UIDraw.begin(graphics);
        var matrix = graphics.pose().last().pose();
        if (Math.abs((long) x1 - x0) >= Math.abs((long) y1 - y0)) {
            if (x0 <= x1) run(consumer, matrix, x0, y0, x1, y1, t0, t1, thickness, color, false);
            else run(consumer, matrix, x1, y1, x0, y0, 1 - t1, 1 - t0, thickness, color, false);
        } else {
            if (y0 <= y1) run(consumer, matrix, y0, x0, y1, x1, t0, t1, thickness, color, true);
            else run(consumer, matrix, y1, x1, y0, x0, 1 - t1, 1 - t0, thickness, color, true);
        }
        UIDraw.end(graphics);
    }

    private static void run(VertexConsumer consumer, Matrix4f matrix, int a0, int b0, int a1, int b1, float t0, float t1,
                            int thickness, int color, boolean vertical) {
        long da = (long) a1 - a0, db = Math.abs((long) b1 - b0);
        int step = b1 > b0 ? 1 : -1, half = thickness / 2;
        int from = (int) Math.max(a0, a0 + (long) Math.floor(t0 * da) - 1);
        int to = (int) Math.min(a1, a0 + (long) Math.ceil(t1 * da) + 1);
        if (to < from) return;
        long e0 = da / 2, k = (long) from - a0, lag = k * db - e0;
        long moves = lag <= 0 || da == 0 ? 0 : (lag + da - 1) / da;
        long error = e0 - k * db + moves * da;
        int b = (int) (b0 + step * moves), runStart = from;
        for (int a = from; a <= to; a++) {
            error -= db;
            boolean move = error < 0 && a < a1;
            if (move || a == to) {
                if (vertical) UIDraw.quad(consumer, matrix, b - half, runStart, b - half + thickness, a + 1, color);
                else UIDraw.quad(consumer, matrix, runStart, b - half, a + 1, b - half + thickness, color);
                runStart = a + 1;
            }
            if (move) {
                b += step;
                error += da;
            }
        }
    }
}
