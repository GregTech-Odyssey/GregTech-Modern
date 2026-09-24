package com.gregtechceu.gtceu.uipro.animation;

/**
 * 一段动画的时间参数，对应 LDLib2 {@code utils.animation.Animation}。
 *
 * @param duration 时长（秒）
 * @param delay    开始前的延迟（秒）
 * @param ease     缓动曲线
 */
public record Animation(float duration, float delay, IEase ease) {

    public static Animation of(float duration, IEase ease) {
        return new Animation(duration, 0, ease);
    }
}
