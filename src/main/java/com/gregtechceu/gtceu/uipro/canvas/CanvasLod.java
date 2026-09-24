package com.gregtechceu.gtceu.uipro.canvas;

/**
 * 画布内容的细节层级，对应 LDLib2 {@code GraphViewLod}。缩得很小时细节已经看不清却照样要付绘制成本，
 * 画布里的项按 {@link CanvasPainter#lod()} 把自己收成更简单的样子。
 * <p>
 * 层级由"每个世界单位占多少<b>物理</b>像素"决定（画布缩放 × GUI 缩放，见 {@link CanvasView#pixelScale()}），
 * 而不是只看画布缩放：GUI 缩放 4 时 0.3 倍缩放的节点，物理尺寸是 GUI 缩放 2 时的两倍，仍然看得清。
 * <p>
 * 只影响绘制路径，不要借它增删控件或改布局。
 */
public enum CanvasLod {

    /** 全部画出：图标、文字、边框。 */
    FULL,
    /** 能认出轮廓：几块纯色、直线，不画图标和文字。 */
    SIMPLIFIED,
    /** 每项一块纯色。 */
    BLOCK;

    /**
     * 按物理像素比例取层级：低于 {@code blockPixelScale} 为 {@link #BLOCK}，低于 {@code simplifiedPixelScale} 为
     * {@link #SIMPLIFIED}，否则 {@link #FULL}（两个阈值都是不含等号的下界）。
     */
    public static CanvasLod resolve(float pixelScale, float simplifiedPixelScale, float blockPixelScale) {
        if (pixelScale < blockPixelScale) return BLOCK;
        if (pixelScale < simplifiedPixelScale) return SIMPLIFIED;
        return FULL;
    }
}
