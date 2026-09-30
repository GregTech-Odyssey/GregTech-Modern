package com.gregtechceu.gtceu.uipro.animation;

public final class ColorMath {

    private ColorMath() {}

    public static int lerp(int from, int to, float t) {
        int a = Math.round((from >>> 24) + t * ((to >>> 24) - (from >>> 24)));
        int r = Math.round((from >> 16 & 0xFF) + t * ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)));
        int g = Math.round((from >> 8 & 0xFF) + t * ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)));
        int b = Math.round((from & 0xFF) + t * ((to & 0xFF) - (from & 0xFF)));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
