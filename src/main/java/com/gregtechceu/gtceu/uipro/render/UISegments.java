package com.gregtechceu.gtceu.uipro.render;

/**
 * 线段裁剪（Liang–Barsky，两端可用、零分配）：可见时返回 true，outT 写入可见部分的参数区间 [t0, t1]。
 */
public final class UISegments {

    private UISegments() {}

    public static boolean clip(float x0, float y0, float x1, float y1, float l, float t, float r, float b, float[] outT) {
        outT[0] = 0;
        outT[1] = 1;
        float dx = x1 - x0, dy = y1 - y0;
        return edge(-dx, x0 - l, outT) && edge(dx, r - x0, outT) && edge(-dy, y0 - t, outT) && edge(dy, b - y0, outT);
    }

    private static boolean edge(float p, float q, float[] range) {
        if (p == 0) return q >= 0;
        float ratio = q / p;
        if (p < 0) {
            if (ratio > range[1]) return false;
            if (ratio > range[0]) range[0] = ratio;
        } else {
            if (ratio < range[0]) return false;
            if (ratio < range[1]) range[1] = ratio;
        }
        return true;
    }
}
