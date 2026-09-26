package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.ClientActions;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.utils.NumberExpressions;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * 调节器：{@code [-n] 数值 [+n]}，用于可以直接输入的数值设置。子类决定数值的含义和显示方式：
 * <ul>
 * <li>{@link NumberField}：整数调节器（优先级、并行数、超频限制……），单位 1。</li>
 * <li>{@link DecimalField}：小数调节器（角度等），按步进取整。</li>
 * <li>{@link PercentField}：百分数调节器（倍率、比例），直接读写小数倍率，界面按百分数显示，按钮写成 "+1%"。</li>
 * </ul>
 * 调节器本身不带单位：单位写在它上方的标题后面（如"水平角θ（°）"）。输入框支持简写与算式（{@link NumberExpressions}），悬停显示数值范围与步进。
 * 标准高 {@link #HEIGHT}，总宽由构造参数给定，中间输入框吃满其余宽度。内部统一按 long "刻度"计算
 * （整数调节器 1 刻度 = 1，百分数调节器 1 刻度 = 一步），刻度与文字的换算由子类传入的 {@link Scale} 负责。
 * <ul>
 * <li>按钮每次加减的量随修饰键变化：默认 / Shift / Ctrl / Ctrl+Shift 依次取四档步长，按钮上的字跟着按住的键实时变化。</li>
 * <li>输入框确认后提交（{@link TextField#commitOnSubmit}）：输入时只改草稿，回车或点在输入框外才上行，Esc 放弃；
 * 输入中途点加减按钮或滚轮：先提交草稿，再在它的基础上加减（按钮靠 {@code UIClientEvents} 在点击分发前提交，滚轮自己先提交）。</li>
 * <li>数值以服务端为准：按钮、输入都在服务端夹到 [最小值, 最大值] 后写入，再经同步回显。
 * 上下限也由服务端取值下发（可以随机器状态变化，例如并行数上限）。</li>
 * <li>滚轮：鼠标在整个调节器上时滚动加减，步长同样随修饰键变化（不用先点进输入框）。</li>
 * <li>到达边界时对应按钮禁用（统一斜纹，悬停说明已到边界）；整个调节器禁用（{@link #disabled}）时按钮、输入框都不可用。</li>
 * </ul>
 */
public abstract class Adjuster extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;
    /// 原版字体里数字和 "+" "-" "%" 都是 6 像素宽（含 1 像素字距）
    protected static final int GLYPH_WIDTH = 6;

    private static final String AT_MIN = "gtceu.uipro.stepper.at_min";
    private static final String AT_MAX = "gtceu.uipro.stepper.at_max";
    private static final String STEP_TOOLTIP = "gui.widget.incrementButton.default_tooltip";
    private static final String RANGE_TOOLTIP = "gtceu.uipro.adjuster.range";
    private static final String STEP_LINE = "gtceu.uipro.adjuster.step";
    private static final String UNBOUNDED = "gtceu.uipro.adjuster.unbounded";
    /// 滚轮加减的客户端请求。SyncValueHost 段以下的 ID 登记：-1 列表行、-2 步进器、-3/-4 弹出面板、-5 AE 网格、
    /// -6 数值框滚轮（本类）、-7/-8/-9 卡片位（CardHost）、-10 可点击的状态行（StatusLine）
    private static final int WHEEL_ID = SyncValueHost.ID_BASE - 6;

    /**
     * 刻度与文字的换算。构造时传入而不做成抽象方法：父类构造里就要用它算按钮宽度，那时子类字段还没赋值。
     */
    protected interface Scale {

        /** 输入框里显示的数值。 */
        String format(long ticks);

        /** 按钮上一步的文字（不含正负号）。 */
        String formatStep(long ticks);

        /** 输入框上行的文字换成刻度；无法解析时返回 null。 */
        @Nullable
        Long parse(String text);

        /** 草稿里允许出现的文字（整段判断，逐字输入的中间态也要允许，如 "-"、"1."）。 */
        boolean acceptsDraft(String draft);

        /** 设置输入框的文字校验（上下限为刻度，上下限变化时重设）。现有两种刻度都原样放行，由 {@link #parse} 和服务端夹取把关。 */
        void restrictInput(TextFieldWidget input, long min, long max);
    }

    private final Scale scale;
    private final LongSupplier getter;
    private final LongConsumer setter;
    private final LongSupplier serverMin;
    private final LongSupplier serverMax;
    private final long[] steps;
    private final SyncValue<Long> min;
    private final SyncValue<Long> max;
    private final TextField field;
    /// 调用方给的输入框说明（范围、步进两行由本类自动加在后面）
    private Component[] tooltips = new Component[0];
    /// 输入框文字缓存：服务端每刻取一次值比较，数值不变就复用同一个字符串，不每刻新建
    private long shownTicks;
    @Nullable
    private String shownText;

    /**
     * @param width  总宽
     * @param scale  刻度与文字的换算
     * @param getter 服务端取值（刻度）
     * @param setter 服务端写值（刻度，已夹到上下限内）
     * @param min    最小值（刻度，服务端取值，下发给客户端）
     * @param max    最大值（刻度，服务端取值，下发给客户端）
     * @param steps  四档步长（刻度；默认 / Shift / Ctrl / Ctrl+Shift），不足四个时沿用最后一个
     */
    protected Adjuster(int width, Scale scale, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max,
                       long[] steps) {
        this.scale = scale;
        this.getter = getter;
        this.setter = setter;
        this.serverMin = min;
        this.serverMax = max;
        this.steps = normalizeSteps(steps);
        this.min = addSyncValue(SyncValue.ofLong(min::getAsLong, min.getAsLong()).onChanged(value -> updateRange()));
        this.max = addSyncValue(SyncValue.ofLong(max::getAsLong, max.getAsLong()).onChanged(value -> updateRange()));
        layout(l -> l.row().width(width).height(HEIGHT).gapAll(UISizes.GAP).alignCenter());

        // 增减按钮宽度按最大一档步长的文字定：按住 Shift / Ctrl 时文字变长（如 "+512"），也要完整显示
        int buttonWidth = stepButtonWidth(scale, this.steps);
        var minus = Button.text(buttonWidth, () -> "-" + scale.formatStep(clientStep()))
                .setOnServerClick(click -> change(-step(click)))
                .disabled(() -> getter.getAsLong() <= serverMin.getAsLong(), AT_MIN);
        minus.setHoverTooltips(STEP_TOOLTIP);
        field = new TextField(0, this::valueText, this::input).commitOnSubmit(scale::acceptsDraft);
        field.layout(l -> l.flexGrow(1));
        // 输入框自带的滚轮要先获得焦点才生效、也不认修饰键；由本元素统一处理
        field.getInput().setWheelDur(0);
        var plus = Button.text(buttonWidth, () -> "+" + scale.formatStep(clientStep()))
                .setOnServerClick(click -> change(step(click)))
                .disabled(() -> getter.getAsLong() >= serverMax.getAsLong(), AT_MAX);
        plus.setHoverTooltips(STEP_TOOLTIP);
        addChildren(minus, field, plus);
        setHoverTooltips(new Component[0]);
        updateRange();
    }

    public TextField getField() {
        return field;
    }

    /** 提示挂在输入框上（LDLib1 只显示鼠标下最内层控件的提示），末尾总带两行灰字：数值范围、步进。 */
    @Override
    public Widget setHoverTooltips(Component... tooltipText) {
        this.tooltips = tooltipText;
        refreshTooltips();
        return this;
    }

    /** 重建输入框提示：调用方给的说明 + 范围 + 步进（上下限由服务端下发，变了重建）。 */
    private void refreshTooltips() {
        if (field == null) return;
        var lines = Arrays.copyOf(tooltips, tooltips.length + 2);
        lines[tooltips.length] = Component.translatable(RANGE_TOOLTIP, boundText(min.getValue(), Long.MIN_VALUE), boundText(max.getValue(), Long.MAX_VALUE))
                .withStyle(ChatFormatting.GRAY);
        lines[tooltips.length + 1] = Component.translatable(STEP_LINE, scale.formatStep(steps[0]), scale.formatStep(steps[1]),
                scale.formatStep(steps[2]), scale.formatStep(steps[3])).withStyle(ChatFormatting.GRAY);
        field.setHoverTooltips(lines);
    }

    /// 上下限是 long 的边界时写"不限"，不写 19 位数字
    private Component boundText(long ticks, long unbounded) {
        return ticks == unbounded ? Component.translatable(UNBOUNDED) : Component.literal(scale.formatStep(ticks));
    }

    /** 放得下 "±最大一步" 的按钮宽度：每个字 {@link #GLYPH_WIDTH}，两侧各留 {@link UISizes#TEXT_PADDING}。 */
    public int inlineWidth() {
        int digits = Math.max(scale.format(max.getValue()).length(), scale.format(min.getValue()).length());
        return 2 * stepButtonWidth(scale, steps) + 2 * UISizes.GAP + Math.max(UISizes.VALUE_WIDTH, digits * GLYPH_WIDTH + 2 * UISizes.TEXT_PADDING);
    }

    private static int stepButtonWidth(Scale scale, long[] steps) {
        long max = 1;
        for (long step : steps) max = Math.max(max, step);
        int chars = 1 + scale.formatStep(max).length();
        return Math.max(UISizes.ICON_BUTTON, chars * GLYPH_WIDTH + 2 * UISizes.TEXT_PADDING);
    }

    private static long[] normalizeSteps(long[] steps) {
        var result = new long[4];
        for (int i = 0; i < 4; i++) {
            result[i] = steps.length == 0 ? 1 : Math.max(1, steps[Math.min(i, steps.length - 1)]);
        }
        return result;
    }

    private String valueText() {
        long ticks = getter.getAsLong();
        if (shownText == null || ticks != shownTicks) {
            shownTicks = ticks;
            shownText = scale.format(ticks);
        }
        return shownText;
    }

    private long step(ClickData click) {
        return steps[(click.isCtrlClick ? 2 : 0) + (click.isShiftClick ? 1 : 0)];
    }

    /**
     * 滚轮：鼠标在整个调节器（两侧按钮和输入框）上时，向上加、向下减，步长同样随 Shift / Ctrl 变化；
     * 客户端只上报方向和修饰键，服务端按步长计算并夹到上下限。输入框自带的滚轮（要先点进去才生效、不认修饰键）不再使用。
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverElement(mouseX, mouseY) || wheelDelta == 0 || isDisabled()) return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        boolean up = wheelDelta > 0, shift = GTUtil.isShiftDown(), ctrl = GTUtil.isCtrlDown();
        commitDraft();
        Consumer<FriendlyByteBuf> writer = buf -> {
            buf.writeBoolean(up);
            buf.writeBoolean(shift);
            buf.writeBoolean(ctrl);
        };
        if (!ClientActions.handleLocally(this, WHEEL_ID, writer)) writeClientAction(WHEEL_ID, writer);
        return true;
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id != WHEEL_ID) {
            super.handleClientAction(id, buffer);
            return;
        }
        boolean up = buffer.readBoolean(), shift = buffer.readBoolean(), ctrl = buffer.readBoolean();
        // 服务端再判一次禁用（客户端可以伪造请求）
        if (isDisabled()) return;
        long step = steps[(ctrl ? 2 : 0) + (shift ? 1 : 0)];
        change(up ? step : -step);
    }

    /**
     * 客户端：滚轮加减前先提交输入框里的草稿（同一连接上按先后顺序到达，服务端先写草稿再加减）。
     * 只在客户端的滚轮处理里调用。
     */
    private void commitDraft() {
        field.commitDraft();
    }

    /** 客户端：按当前按住的修饰键取步长，只在绘制按钮文字时调用。 */
    private long clientStep() {
        return steps[(GTUtil.isCtrlDown() ? 2 : 0) + (GTUtil.isShiftDown() ? 1 : 0)];
    }

    /** 服务端：加减一步（饱和加法，不溢出）。 */
    private void change(long delta) {
        long current = getter.getAsLong();
        long next = delta > 0 ? (current > Long.MAX_VALUE - delta ? Long.MAX_VALUE : current + delta) :
                (current < Long.MIN_VALUE - delta ? Long.MIN_VALUE : current + delta);
        write(next);
    }

    /** 服务端：输入框上行的文字。 */
    private void input(String text) {
        var ticks = scale.parse(text.trim());
        if (ticks != null) write(ticks);
    }

    private void write(long ticks) {
        long clamped = Math.max(serverMin.getAsLong(), Math.min(serverMax.getAsLong(), ticks));
        if (clamped != getter.getAsLong()) setter.accept(clamped);
    }

    /** 输入框只接受上下限内的数（超出时夹到边界）；上下限变了重新设。 */
    private void updateRange() {
        if (field == null) return;
        scale.restrictInput(field.getInput(), min.getValue(), max.getValue());
        // 限制输入时 LDLib 会顺带打开输入框自带的滚轮，关掉（滚轮由本元素处理）
        field.getInput().setWheelDur(0);
        refreshTooltips();
    }
}
