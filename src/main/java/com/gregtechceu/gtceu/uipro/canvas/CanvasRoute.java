package com.gregtechceu.gtceu.uipro.canvas;

/**
 * 连线的走法（折线顶点），思路来自 LDLib2 {@code WireRouter}：沿端点自身的方向出发，在两端之间的"干线"上换道。
 * 结果是 {@link CanvasPainter#path} 能直接画的 x0, y0, x1, y1, ... 数组；与界面、Minecraft 无关。
 */
public final class CanvasRoute {

    private CanvasRoute() {}

    /**
     * 横向流向的正交折线：从起点水平出发，在 {@code trunkX} 处竖直换道，再水平到终点（横-竖-横）。
     * 起终点同高时只有一段横线。
     */
    public static float[] horizontal(float fromX, float fromY, float toX, float toY, float trunkX) {
        if (fromY == toY) return new float[] { fromX, fromY, toX, toY };
        return new float[] { fromX, fromY, trunkX, fromY, trunkX, toY, toX, toY };
    }

    /** 同 {@link #horizontal(float, float, float, float, float)}，干线取两端中点。 */
    public static float[] horizontal(float fromX, float fromY, float toX, float toY) {
        return horizontal(fromX, fromY, toX, toY, (fromX + toX) / 2);
    }

    /** 纵向流向的正交折线（竖-横-竖），干线在 {@code trunkY}。 */
    public static float[] vertical(float fromX, float fromY, float toX, float toY, float trunkY) {
        if (fromX == toX) return new float[] { fromX, fromY, toX, toY };
        return new float[] { fromX, fromY, fromX, trunkY, toX, trunkY, toX, toY };
    }

    /** 折线的包围盒（剔除看不见的连线用），外扩 {@code margin}。 */
    public static CanvasRect bounds(float[] points, float margin) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < points.length; i += 2) {
            minX = Math.min(minX, points[i]);
            maxX = Math.max(maxX, points[i]);
            minY = Math.min(minY, points[i + 1]);
            maxY = Math.max(maxY, points[i + 1]);
        }
        return new CanvasRect(minX - margin, minY - margin, maxX - minX + 2 * margin, maxY - minY + 2 * margin);
    }
}
