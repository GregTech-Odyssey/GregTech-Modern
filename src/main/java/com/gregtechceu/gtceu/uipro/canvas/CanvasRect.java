package com.gregtechceu.gtceu.uipro.canvas;

import org.jetbrains.annotations.Nullable;

/**
 * 画布世界坐标里的矩形（左上角 + 宽高），画布里的项、内容范围、可见区域都用它表示。
 */
public record CanvasRect(float x, float y, float width, float height) {

    public static CanvasRect of(float x, float y, float width, float height) {
        return new CanvasRect(x, y, width, height);
    }

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width / 2;
    }

    public float centerY() {
        return y + height / 2;
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    public boolean intersects(CanvasRect other) {
        return other.x < x + width && other.x + other.width > x && other.y < y + height && other.y + other.height > y;
    }

    /** 四周各外扩 {@code margin}（负数为内收）。 */
    public CanvasRect inflate(float margin) {
        return new CanvasRect(x - margin, y - margin, width + 2 * margin, height + 2 * margin);
    }

    /** 包住两个矩形的最小矩形；{@code other} 为 null 时返回自身。 */
    public CanvasRect union(@Nullable CanvasRect other) {
        if (other == null) return this;
        float l = Math.min(x, other.x), t = Math.min(y, other.y);
        return new CanvasRect(l, t, Math.max(right(), other.right()) - l, Math.max(bottom(), other.bottom()) - t);
    }

    /** {@code a} 与 {@code b} 的并集，任一为 null 时返回另一个。 */
    @Nullable
    public static CanvasRect union(@Nullable CanvasRect a, @Nullable CanvasRect b) {
        return a == null ? b : a.union(b);
    }
}
