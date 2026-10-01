package com.gregtechceu.gtceu.uipro.animation;

import net.minecraft.Util;

public final class UIClock {

    public static final double MS_PER_RADIAN = 200;

    private static long frameMillis = Util.getMillis();

    private UIClock() {}

    public static void beginFrame() {
        frameMillis = Util.getMillis();
    }

    public static long millis() {
        return frameMillis;
    }

    public static double phase(long periodMillis) {
        return (frameMillis % periodMillis) / (double) periodMillis;
    }

    public static float pulse() {
        return 0.5f + (float) Math.sin(frameMillis / MS_PER_RADIAN) * 0.5f;
    }

    public static int breathe(int low, int high) {
        return ColorMath.lerp(low, high, pulse());
    }

    public static int mix(int highlight, int base) {
        return ColorMath.lerp(highlight, base, 1 - pulse());
    }
}
