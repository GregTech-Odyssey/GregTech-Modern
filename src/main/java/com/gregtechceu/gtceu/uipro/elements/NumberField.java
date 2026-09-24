package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.utils.NumberExpressions;

import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * 整数调节器 {@code [-n] 数值 [+n]}（GTM {@code NumberInputWidget} 的新式版本），所有整数输入统一用它：
 * 优先级、并行数、超频限制、配置数量等。单位 1，默认步长 1 / 8 / 64 / 512（默认 / Shift / Ctrl / Ctrl+Shift）。
 * 输入框支持简写与算式（如 {@code 1.5k}、{@code 2*64}，见 {@link NumberExpressions}），结果向下取整。
 * 通用行为（确认后提交、服务端夹取、上下限下发、滚轮、边界禁用）见 {@link Adjuster}；小数用 {@link DecimalField}。
 */
public class NumberField extends Adjuster {

    /** 默认步长：默认 / Shift / Ctrl / Ctrl+Shift。 */
    public static final long[] DEFAULT_STEPS = { 1, 8, 64, 512 };

    /// 纯整数走快速路径（不经算式解析）
    private static final Pattern INTEGER_TEXT = Pattern.compile("-?\\d{1,30}");
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    /// 整数：刻度就是数值本身
    private static final Scale INTEGER = new Scale() {

        @Override
        public String format(long ticks) {
            return Long.toString(ticks);
        }

        @Override
        public String formatStep(long ticks) {
            return Long.toString(ticks);
        }

        /// 超出 long 的饱和到边界（服务端再夹到上下限），而不是解析失败被还原
        @Override
        public @Nullable Long parse(String text) {
            BigInteger value;
            if (INTEGER_TEXT.matcher(text).matches()) {
                value = new BigInteger(text);
            } else {
                BigDecimal result = NumberExpressions.evaluate(text);
                if (result == null) return null;
                value = result.setScale(0, RoundingMode.FLOOR).toBigInteger();
            }
            if (value.compareTo(LONG_MAX) > 0) return Long.MAX_VALUE;
            if (value.compareTo(LONG_MIN) < 0) return Long.MIN_VALUE;
            return value.longValue();
        }

        @Override
        public boolean acceptsDraft(String draft) {
            return NumberExpressions.acceptsDraft(draft);
        }

        /// 不用 LDLib 的范围校验（不认简写和算式、超出 long 时解析失败会被还原）：原样交给 parse，服务端再夹到上下限
        @Override
        public void restrictInput(TextFieldWidget input, long min, long max) {
            input.setValidator(text -> text);
        }
    };

    /**
     * @param width  总宽
     * @param getter 服务端取值
     * @param setter 服务端写值（已夹到上下限内）
     * @param min    最小值（服务端取值，下发给客户端）
     * @param max    最大值（服务端取值，下发给客户端）
     * @param steps  四档步长（默认 / Shift / Ctrl / Ctrl+Shift），不传用 {@link #DEFAULT_STEPS}，不足四个时沿用最后一个
     */
    public NumberField(int width, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, long... steps) {
        super(width, INTEGER, getter, setter, min, max, steps.length == 0 ? DEFAULT_STEPS : steps);
    }

    /** 固定上下限、默认步长。 */
    public static NumberField of(int width, LongSupplier getter, LongConsumer setter, long min, long max) {
        return new NumberField(width, getter, setter, () -> min, () -> max, DEFAULT_STEPS);
    }
}
