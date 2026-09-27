package com.gregtechceu.gtceu.uipro.canvas;

public final class CanvasPulse {

    public static final double MS_PER_RADIAN = 200;

    private CanvasPulse() {}

    public static float phase() {
        return 0.5f + (float) Math.sin(System.currentTimeMillis() / MS_PER_RADIAN) * 0.5f;
    }

    public static int mix(int highlight, int base) {
        return lerp(highlight, base, 1 - phase());
    }

    public static int breathe(int low, int high) {
        return lerp(low, high, phase());
    }

    public static int lerp(int from, int to, float ratio) {
        int a1 = from >> 24 & 0xFF, r1 = from >> 16 & 0xFF, g1 = from >> 8 & 0xFF, b1 = from & 0xFF;
        int a2 = to >> 24 & 0xFF, r2 = to >> 16 & 0xFF, g2 = to >> 8 & 0xFF, b2 = to & 0xFF;
        int a = (int) (a1 + ratio * (a2 - a1));
        int r = (int) (r1 + ratio * (r2 - r1));
        int g = (int) (g1 + ratio * (g2 - g1));
        int b = (int) (b1 + ratio * (b2 - b1));
        return r << 16 | g << 8 | b | a << 24;
    }
}
