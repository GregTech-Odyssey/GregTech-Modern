package com.gregtechceu.gtceu.uipro.animation;

/**
 * 常用缓动曲线，对应 LDLib2 {@code math.interpolate.Eases}（同名、同公式）。输入、输出都在 [0, 1]。
 */
public enum Eases implements IEase {

    LINEAR(t -> t),
    QUAD_IN(t -> t * t),
    QUAD_OUT(t -> -t * (t - 2)),
    QUAD_IN_OUT(t -> t < 0.5f ? 2 * t * t : -2 * t * t + 4 * t - 1),
    CUBIC_IN(t -> t * t * t),
    CUBIC_OUT(t -> (t - 1) * (t - 1) * (t - 1) + 1),
    CUBIC_IN_OUT(t -> t < 0.5f ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1),
    QUART_OUT(t -> 1 - (t - 1) * (t - 1) * (t - 1) * (t - 1)),
    EXPO_OUT(t -> t == 1 ? 1 : 1 - (float) Math.pow(2, -10 * t)),
    SINE_IN_OUT(t -> -0.5f * ((float) Math.cos(Math.PI * t) - 1));

    private final IEase ease;

    Eases(IEase ease) {
        this.ease = ease;
    }

    @Override
    public float interpolate(float t) {
        return ease.interpolate(t);
    }
}
