package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.ClientActions;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/**
 * 数值输入 {@code [-n] 数值 [+n]}（GTM {@code NumberInputWidget} 的新式版本），用于优先级、并行数、超频限制这类可以直接输入的数值。
 * <p>
 * 标准高 {@link #HEIGHT}，总宽由构造参数给定，中间输入框吃满两侧按钮之外的宽度。
 * <ul>
 * <li>按钮每次加减的量随修饰键变化：默认 / Shift / Ctrl / Ctrl+Shift 依次取 {@code steps} 的四个值（默认 1、8、64、512），
 * 按钮上的字跟着按住的键实时变化。</li>
 * <li>数值以服务端为准：按钮、输入都在服务端夹到 [最小值, 最大值] 后写入 {@code setter}，再经同步回显。
 * 上下限也由服务端取值下发（可以随机器状态变化，例如并行数上限）。</li>
 * <li>滚轮：鼠标在整个输入上时滚动加减，步长同样随修饰键变化（不用先点进输入框）。</li>
 * <li>到达边界时对应按钮禁用（统一斜纹，悬停说明已到边界）；整个输入禁用（{@link #disabled}）时按钮、输入框都不可用。</li>
 * </ul>
 */
public class NumberField extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;
    /// 原版字体里数字和 "+" "-" 都是 6 像素宽（含 1 像素字距）
    private static final int GLYPH_WIDTH = 6;
    /** 默认步长：默认 / Shift / Ctrl / Ctrl+Shift。 */
    public static final long[] DEFAULT_STEPS = { 1, 8, 64, 512 };

    private static final String AT_MIN = "gtceu.uipro.stepper.at_min";
    private static final String AT_MAX = "gtceu.uipro.stepper.at_max";
    private static final String STEP_TOOLTIP = "gui.widget.incrementButton.default_tooltip";

    private final LongSupplier getter;
    private final LongConsumer setter;
    private final LongSupplier serverMin;
    private final LongSupplier serverMax;
    private final long[] steps;
    private final SyncValue<Long> min;
    private final SyncValue<Long> max;
    private final TextField field;
    /// 滚轮加减的客户端请求（登记见 AEStackGrid：ID_BASE-1 列表行、-2 步进器、-3/-4 弹出面板、-5 AE 网格）
    private static final int WHEEL_ID = SyncValueHost.ID_BASE - 6;

    /**
     * @param width  总宽
     * @param getter 服务端取值
     * @param setter 服务端写值（已夹到上下限内）
     * @param min    最小值（服务端取值，下发给客户端）
     * @param max    最大值（服务端取值，下发给客户端）
     * @param steps  四档步长（默认 / Shift / Ctrl / Ctrl+Shift），不足四个时沿用最后一个
     */
    public NumberField(int width, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, long... steps) {
        this.getter = getter;
        this.setter = setter;
        this.serverMin = min;
        this.serverMax = max;
        this.steps = normalizeSteps(steps);
        this.min = addSyncValue(SyncValue.ofLong(min::getAsLong, min.getAsLong()).onChanged(value -> updateRange()));
        this.max = addSyncValue(SyncValue.ofLong(max::getAsLong, max.getAsLong()).onChanged(value -> updateRange()));
        layout(l -> l.row().width(width).height(HEIGHT).gapAll(UISizes.GAP).alignCenter());

        // 增减按钮宽度按最大一档步长的位数定：按住 Shift / Ctrl 时文字变长（如 "+512"），也要完整显示
        int buttonWidth = stepButtonWidth(this.steps);
        var minus = Button.text(buttonWidth, () -> "-" + clientStep())
                .setOnServerClick(click -> change(-step(click)))
                .disabled(() -> getter.getAsLong() <= serverMin.getAsLong(), AT_MIN);
        minus.setHoverTooltips(STEP_TOOLTIP);
        field = new TextField(0, () -> Long.toString(getter.getAsLong()), this::input);
        field.layout(l -> l.flexGrow(1));
        // 输入框自带的滚轮要先获得焦点才生效、也不认修饰键；由本元素统一处理
        field.getInput().setWheelDur(0);
        var plus = Button.text(buttonWidth, () -> "+" + clientStep())
                .setOnServerClick(click -> change(step(click)))
                .disabled(() -> getter.getAsLong() >= serverMax.getAsLong(), AT_MAX);
        plus.setHoverTooltips(STEP_TOOLTIP);
        addChildren(minus, field, plus);
        updateRange();
    }

    /** 固定上下限、默认步长。 */
    public static NumberField of(int width, LongSupplier getter, LongConsumer setter, long min, long max) {
        return new NumberField(width, getter, setter, () -> min, () -> max, DEFAULT_STEPS);
    }

    public TextField getField() {
        return field;
    }

    /** 提示挂在输入框上：LDLib1 只显示鼠标下最内层控件的提示。 */
    @Override
    public Widget setHoverTooltips(Component... tooltipText) {
        field.setHoverTooltips(tooltipText);
        return this;
    }

    /** 放得下 "±最大步长" 的按钮宽度：符号加位数，每个字 {@link #GLYPH_WIDTH}，两侧各留 {@link UISizes#TEXT_PADDING}。 */
    public static int stepButtonWidth(long[] steps) {
        long max = 1;
        for (long step : steps) max = Math.max(max, step);
        int chars = 1 + Long.toString(max).length();
        return Math.max(UISizes.ICON_BUTTON, chars * GLYPH_WIDTH - 1 + 2 * UISizes.TEXT_PADDING);
    }

    private static long[] normalizeSteps(long[] steps) {
        var result = new long[4];
        for (int i = 0; i < 4; i++) {
            result[i] = steps.length == 0 ? DEFAULT_STEPS[i] : Math.max(1, steps[Math.min(i, steps.length - 1)]);
        }
        return result;
    }

    private long step(ClickData click) {
        return steps[(click.isCtrlClick ? 2 : 0) + (click.isShiftClick ? 1 : 0)];
    }

    /**
     * 滚轮：鼠标在整个输入（两侧按钮和输入框）上时，向上加、向下减，步长同样随 Shift / Ctrl 变化；
     * 客户端只上报方向和修饰键，服务端按步长计算并夹到上下限。输入框自带的滚轮（要先点进去才生效、不认修饰键）不再使用。
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverElement(mouseX, mouseY) || wheelDelta == 0 || isDisabled()) return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        boolean up = wheelDelta > 0, shift = GTUtil.isShiftDown(), ctrl = GTUtil.isCtrlDown();
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
        try {
            write(Long.parseLong(text.trim()));
        } catch (NumberFormatException ignored) {}
    }

    private void write(long value) {
        long clamped = Math.max(serverMin.getAsLong(), Math.min(serverMax.getAsLong(), value));
        if (clamped != getter.getAsLong()) setter.accept(clamped);
    }

    /** 输入框只接受上下限内的整数（超出时夹到边界）；上下限变了重新设。 */
    private void updateRange() {
        if (field == null) return;
        // setNumbersOnly 会顺带打开输入框自带的滚轮，关掉（滚轮由本元素处理）
        field.getInput().setNumbersOnly(min.getValue(), max.getValue()).setWheelDur(0);
    }
}
