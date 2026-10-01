package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.animation.UIClock;
import com.gregtechceu.gtceu.uipro.render.UIPixels;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CanvasWire {

    private static final float GLOW = 1;
    private static final float DASH = 3, DASH_GAP = 2;
    private static final float DOT = 1, DOT_GAP = 2;
    private static final float SPARK_SPACING = 18;
    private static final float SPARK_HEAD = 3;
    private static final float SPARK_TAIL = 5;
    private static final long SPARK_PERIOD_MS = 750;

    private CanvasWire() {}

    public static void glow(CanvasPainter painter, float[] points, float width, WireStyle style) {
        if (!style.glows()) return;
        painter.path(points, painter.atLeastPixel(width) + 2 * painter.px(GLOW), UIClock.breathe(style.glowLow(), style.glowHigh()));
    }

    public static void core(CanvasPainter painter, float[] points, float width, WireStyle style, float origin) {
        switch (style.pattern()) {
            case SOLID -> painter.path(points, width, style.core());
            case DASHED -> dashes(painter, points, width, style.core(), origin, DASH, DASH_GAP);
            case DOTTED -> dashes(painter, points, width, style.core(), origin, DOT, DOT_GAP);
        }
    }

    public static void sparks(CanvasPainter painter, float[] points, float width, WireStyle style, float origin) {
        if (!style.flows()) return;
        float length = CanvasPainter.pathLength(points);
        float shift = (float) UIClock.phase(SPARK_PERIOD_MS) * SPARK_SPACING;
        float first = shift - floorMod(origin, SPARK_SPACING);
        for (float head = first; head - SPARK_HEAD - SPARK_TAIL < length; head += SPARK_SPACING) {
            if (head <= 0) continue;
            float snapped = UIPixels.snap(head);
            float neck = snapped - painter.px(SPARK_HEAD);
            painter.pathSpan(points, Math.max(0, neck - painter.px(SPARK_TAIL)), Math.min(length, neck), width, style.sparkTail());
            painter.pathSpan(points, Math.max(0, neck), Math.min(length, snapped), width, style.spark());
        }
    }

    private static void dashes(CanvasPainter painter, float[] points, float width, int color, float origin, float dash, float gap) {
        float length = CanvasPainter.pathLength(points);
        float period = dash + gap;
        for (float start = -floorMod(origin, period); start < length; start += period) {
            painter.pathSpan(points, Math.max(0, start), Math.min(length, start + dash), width, color);
        }
    }

    private static float floorMod(float value, float period) {
        float mod = value % period;
        return mod < 0 ? mod + period : mod;
    }
}
