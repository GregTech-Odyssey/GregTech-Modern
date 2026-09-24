package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.utils.NumberExpressions;

import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 小数调节器 {@code [-0.1] 12.5 [+0.1]}：读写 double，按步进取整。角度、长度等非整数设置统一用它
 * （单位写在上方标题后面，如"水平角θ（°）"）；百分数（倍率、比例）用子类 {@link PercentField}，整数用 {@link NumberField}。
 * <ul>
 * <li>步进（如 0.1）决定精度：数值按步进四舍五入，显示的小数位数与步进一致（0.1 → 一位小数）。</li>
 * <li>四档（默认 / Shift / Ctrl / Ctrl+Shift）的步数默认 {@link #DEFAULT_STEP_COUNTS}，可只给前几档（后面沿用最后一档）；
 * 按钮上的步长去掉末尾的 0（"+1" 而不是 "+1.0"）。</li>
 * <li>输入框支持简写与算式（{@link NumberExpressions}），结果按步进四舍五入，服务端再夹到上下限；
 * 通用行为（确认后提交、上下限下发、滚轮、边界禁用）见 {@link Adjuster}。</li>
 * </ul>
 */
public class DecimalField extends Adjuster {

    /** 默认四档步数：默认 / Shift / Ctrl / Ctrl+Shift。 */
    public static final long[] DEFAULT_STEP_COUNTS = { 1, 10, 100, 1000 };

    /**
     * @param width      总宽
     * @param getter     服务端取值
     * @param setter     服务端写值（已按步进取整并夹到上下限内）
     * @param min        最小值（服务端取值，下发给客户端）
     * @param max        最大值（服务端取值，下发给客户端）
     * @param step       步进，必须大于 0
     * @param stepCounts 四档步数，不传用 {@link #DEFAULT_STEP_COUNTS}
     */
    public DecimalField(int width, DoubleSupplier getter, DoubleConsumer setter, DoubleSupplier min, DoubleSupplier max,
                        double step, long... stepCounts) {
        this(width, getter, setter, min, max, step, 1, "", stepCounts);
    }

    /**
     * @param displayFactor 显示倍数：界面上的数 = 实际值 × 它（百分数为 100）
     * @param stepSuffix    按钮步长文字的后缀（百分数为 "%"，写成 "+1%"）
     */
    protected DecimalField(int width, DoubleSupplier getter, DoubleConsumer setter, DoubleSupplier min, DoubleSupplier max,
                           double step, int displayFactor, String stepSuffix, long... stepCounts) {
        super(width, new DecimalScale(step, displayFactor, stepSuffix),
                () -> toTicks(getter.getAsDouble(), step), ticks -> setter.accept(fromTicks(ticks, step)),
                () -> toTicks(min.getAsDouble(), step), () -> toTicks(max.getAsDouble(), step),
                stepCounts.length == 0 ? DEFAULT_STEP_COUNTS : stepCounts);
    }

    /** 固定上下限。 */
    public static DecimalField of(int width, DoubleSupplier getter, DoubleConsumer setter, double min, double max,
                                  double step, long... stepCounts) {
        return new DecimalField(width, getter, setter, () -> min, () -> max, step, stepCounts);
    }

    private static long toTicks(double value, double step) {
        return Math.round(value / step);
    }

    /// 用十进制换算，避免 0.01 × 150 这类浮点误差（得到 1.5000000000000002）
    private static double fromTicks(long ticks, double step) {
        return BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(ticks)).doubleValue();
    }

    /** 小数刻度：1 刻度 = 一步；界面上的数 = 刻度 × 步进 × 显示倍数，小数位数跟步进的精度走。 */
    private static final class DecimalScale implements Scale {

        /// 大数除法前的规模上限：整数部分、小数部分各最多 30 位（算式解析器自带上限，这里再兜一层）
        private static final int MAX_DIGITS = 30;

        /// 一步对应的界面数值（步进 0.01、显示倍数 100 → 1）
        private final BigDecimal stepDisplay;
        private final int decimals;
        private final String stepSuffix;

        private DecimalScale(double step, int displayFactor, String stepSuffix) {
            if (!(step > 0)) throw new IllegalArgumentException("step must be positive: " + step);
            stepDisplay = BigDecimal.valueOf(step).multiply(BigDecimal.valueOf(displayFactor)).stripTrailingZeros();
            decimals = Math.max(0, stepDisplay.scale());
            this.stepSuffix = stepSuffix;
        }

        private BigDecimal display(long ticks) {
            return stepDisplay.multiply(BigDecimal.valueOf(ticks)).setScale(decimals, RoundingMode.HALF_UP);
        }

        @Override
        public String format(long ticks) {
            return display(ticks).toPlainString();
        }

        @Override
        public String formatStep(long ticks) {
            return display(ticks).stripTrailingZeros().toPlainString() + stepSuffix;
        }

        @Override
        public boolean acceptsDraft(String draft) {
            return NumberExpressions.acceptsDraft(draft);
        }

        /// 超出 long 的饱和到边界（与整数调节器一致），服务端再夹到上下限
        @Override
        public @Nullable Long parse(String text) {
            BigDecimal value = NumberExpressions.evaluate(text);
            if (value == null || value.precision() - value.scale() > MAX_DIGITS || value.scale() > MAX_DIGITS) return null;
            // 与 toTicks 的 Math.round 一致：半步向正方向取整
            var ticks = value.divide(stepDisplay, 0, value.signum() >= 0 ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN);
            if (ticks.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0) return Long.MAX_VALUE;
            if (ticks.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) <= 0) return Long.MIN_VALUE;
            return ticks.longValue();
        }

        /// 不用 LDLib 的范围校验（不认小数、简写与算式，夹取也会改写文字）：原样交给 parse，服务端再夹到上下限
        @Override
        public void restrictInput(TextFieldWidget input, long min, long max) {
            input.setValidator(text -> text);
        }
    }
}
