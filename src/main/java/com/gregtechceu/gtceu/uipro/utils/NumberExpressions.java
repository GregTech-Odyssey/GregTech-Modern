package com.gregtechceu.gtceu.uipro.utils;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 调节器（{@code Adjuster}）输入框里的数值表达式：简写（k/m/g/t/p = 10³/10⁶/10⁹/10¹²/10¹⁵，不分大小写）与算式。
 * <p>
 * 框架自带的解析只认"数字 + 可选简写"（如 {@code 1.5k}）；整合包可以用 {@link #setEvaluator} 换成完整的算式解析
 * （GTOCore 注册自己的表达式解析器，支持 + - * / ^ &lt;&lt; &gt;&gt; 与括号）。解析发生在服务端（玩家提交的草稿上行后），
 * 所以两端都要注册同一个解析器；解析器必须自带防滥用的上限（位数、指数、位移），这里只限制文本长度。
 */
public final class NumberExpressions {

    /** 输入文本长度上限（草稿与上行文字）。 */
    public static final int MAX_LENGTH = 64;

    /// 草稿里允许的字符：数字、小数点、简写字母、指数 e、运算符、括号、空格
    private static final Pattern DRAFT = Pattern.compile("[0-9.kmgtpKMGTPeE+\\-*/^<>() ]{0," + MAX_LENGTH + "}");
    /// 框架自带：可选负号、数字（可带小数）、可选一个简写字母
    private static final Pattern BASIC = Pattern.compile("(-?(?:\\d{1,18}(?:\\.\\d{0,9})?|\\.\\d{1,9}))([kmgtpKMGTP]?)");
    private static final BigDecimal[] SUFFIX = { BigDecimal.ONE, new BigDecimal("1E3"), new BigDecimal("1E6"),
            new BigDecimal("1E9"), new BigDecimal("1E12"), new BigDecimal("1E15") };

    private static Function<String, BigDecimal> evaluator = NumberExpressions::basic;

    private NumberExpressions() {}

    /** 换成完整的算式解析（两端都要调用）。解析失败时应抛出 {@link RuntimeException}。 */
    public static void setEvaluator(Function<String, BigDecimal> evaluator) {
        NumberExpressions.evaluator = Objects.requireNonNull(evaluator);
    }

    /** 草稿里能不能出现这段文字（逐字输入的中间态也要放行）。 */
    public static boolean acceptsDraft(String draft) {
        return DRAFT.matcher(draft).matches();
    }

    /** 解析一段文字；空、过长、解析失败都返回 null。 */
    public static @Nullable BigDecimal evaluate(String text) {
        text = text.trim();
        if (text.isEmpty() || text.length() > MAX_LENGTH) return null;
        try {
            return evaluator.apply(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static BigDecimal basic(String text) {
        var matcher = BASIC.matcher(text);
        if (!matcher.matches()) throw new IllegalArgumentException(text);
        var value = new BigDecimal(matcher.group(1));
        String suffix = matcher.group(2);
        if (suffix.isEmpty()) return value;
        return value.multiply(SUFFIX[1 + "kmgtp".indexOf(Character.toLowerCase(suffix.charAt(0)))]);
    }
}
