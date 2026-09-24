package com.gregtechceu.gtceu.uipro.animation;

/** 缓动函数，对应 LDLib2 {@code IEase}：把 [0, 1] 的时间进度映射成 [0, 1] 的数值进度。 */
@FunctionalInterface
public interface IEase {

    float interpolate(float t);
}
