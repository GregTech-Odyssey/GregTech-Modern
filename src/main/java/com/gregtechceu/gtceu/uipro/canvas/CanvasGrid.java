package com.gregtechceu.gtceu.uipro.canvas;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * 画布背景网格的密度计算，算法照搬 LDLib2 {@code GraphView}（自适应密度 + 三级交叉淡入）：
 * <ul>
 * <li>固定世界间距的网格只在一种缩放下好看：缩小挤成摩尔纹，放大只剩几条线。所以间距按
 * {@link #subdivisions} 的整数次幂取，保证屏幕上的线距不小于 {@link #minPixels}。</li>
 * <li>换级时不能突变：最密一级从无渐显，中间一级从细线色渐变到主线色，最粗一级始终是主线色；
 * 每级跳过由上一级负责的线，同一条线不会叠画两次（叠画会让共享线随渐变变亮、换级时再跳回去）。</li>
 * </ul>
 * 与 Minecraft、界面无关，只做数学；绘制在 {@link CanvasView}。结果按缩放缓存，缩放不变时每帧不重算、不分配。
 */
public final class CanvasGrid {

    /** 一级网格：间距（世界单位）、颜色、每隔几条跳过一条（该线归更粗一级画；0 为不跳）。 */
    public record Level(float cellSize, int color, int skipEvery) {}

    private final float base;
    private final float minPixels;
    private final int subdivisions;
    private final int lineColor;
    private final int accentColor;
    private float cachedScale = Float.NaN;
    private List<Level> cachedLevels = List.of();

    /**
     * @param base         最细一级的基准间距（世界单位）
     * @param minPixels    屏幕上允许的最小线距（界面像素）
     * @param subdivisions 相邻两级的倍数（4 表示粗一级的一格含 4×4 细格），至少 2
     * @param lineColor    细线色（ARGB）
     * @param accentColor  主线色（ARGB）
     */
    public CanvasGrid(float base, float minPixels, int subdivisions, int lineColor, int accentColor) {
        this.base = Math.max(0.001f, base);
        this.minPixels = Math.max(1, minPixels);
        this.subdivisions = Math.max(2, subdivisions);
        this.lineColor = lineColor;
        this.accentColor = accentColor;
    }

    /** 缩放为 {@code scale} 时要画的各级（从密到疏）。 */
    public List<Level> levels(float scale) {
        if (scale != cachedScale) {
            cachedScale = scale;
            cachedLevels = computeLevels(scale);
        }
        return cachedLevels;
    }

    private List<Level> computeLevels(float scale) {
        double level = Math.ceil(Math.log(minPixels / (base * scale)) / Math.log(subdivisions));
        float fine = base * (float) Math.pow(subdivisions, level);
        float fade = Mth.clamp((fine * scale - minPixels) / (minPixels * (subdivisions - 1f)), 0f, 1f);
        float coarse = fine * subdivisions;
        var levels = new ArrayList<Level>(3);
        if (fade > 0.004f) levels.add(new Level(fine, withAlphaFactor(lineColor, fade), subdivisions));
        levels.add(new Level(coarse, blend(lineColor, accentColor, fade), subdivisions));
        levels.add(new Level(coarse * subdivisions, accentColor, 0));
        return levels;
    }

    private static int withAlphaFactor(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Mth.clamp(factor, 0f, 1f));
        return alpha << 24 | (color & 0xFFFFFF);
    }

    /** 逐通道（含透明度）线性插值。 */
    static int blend(int from, int to, float t) {
        int a = Math.round(Mth.lerp(t, from >>> 24, to >>> 24));
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return a << 24 | r << 16 | g << 8 | b;
    }
}
