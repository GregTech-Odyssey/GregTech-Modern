package com.gregtechceu.gtceu.uipro.elements;

import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 百分数调节器 {@code [-1%] 150 % [+1%]}：小数调节器（{@link DecimalField}）的百分数版，直接读写小数倍率（{@code 1.0} = 100%），
 * 界面上按百分数显示。为了让玩家清楚调的是百分比：输入框右侧固定显示 "%"，增减按钮写成 "+1%"，悬停说明"100% = 1 倍"。
 * <p>
 * 步进默认 {@link #DEFAULT_STEP}（0.01 = 1%），可设置；四档步数同 {@link DecimalField}。
 * 输入框里填百分数（不带 %），可以带小数，服务端按步进四舍五入（1% 步进下 "150.4" 记为 150%）。
 */
public class PercentField extends DecimalField {

    /** 默认步进：0.01（1%）。 */
    public static final double DEFAULT_STEP = 0.01;

    private static final String TOOLTIP = "gtceu.uipro.percent_field.tooltip";

    /**
     * @param width      总宽
     * @param getter     服务端取值（倍率，1.0 = 100%）
     * @param setter     服务端写值（倍率，已按步进取整并夹到上下限内）
     * @param min        最小倍率（服务端取值，下发给客户端）
     * @param max        最大倍率（服务端取值，下发给客户端）
     * @param step       步进（倍率，0.01 = 1%），必须大于 0
     * @param stepCounts 四档步数，不传用 {@link DecimalField#DEFAULT_STEP_COUNTS}
     */
    public PercentField(int width, DoubleSupplier getter, DoubleConsumer setter, DoubleSupplier min, DoubleSupplier max,
                        double step, long... stepCounts) {
        super(width, getter, setter, min, max, step, 100, "%", stepCounts);
        setHoverTooltips(Component.translatable(TOOLTIP));
    }

    /** 固定上下限、默认步进（1%）。 */
    public static PercentField of(int width, DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new PercentField(width, getter, setter, () -> min, () -> max, DEFAULT_STEP);
    }

    /** 固定上下限、指定步进与四档步数。 */
    public static PercentField of(int width, DoubleSupplier getter, DoubleConsumer setter, double min, double max, double step, long... stepCounts) {
        return new PercentField(width, getter, setter, () -> min, () -> max, step, stepCounts);
    }
}
