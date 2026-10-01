package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.uipro.elements.Adjuster;
import com.gregtechceu.gtceu.uipro.elements.DecimalField;
import com.gregtechceu.gtceu.uipro.elements.NumberField;

import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.network.FriendlyByteBuf;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A widget containing an integer input field, as well as adjacent buttons for increasing or decreasing the value.
 *
 * <p>
 * The buttons' change amount can be altered with Ctrl, Shift, or both.<br>
 * The input is limited by a minimum and maximum value.
 * </p>
 */
public abstract class NumberInputWidget<T extends Number> extends WidgetGroup {

    protected abstract T defaultMin();

    protected abstract T defaultMax();

    protected abstract String toText(T value);

    protected abstract T fromText(String value);

    protected record ChangeValues<T extends Number>(T regular, T shift, T ctrl, T ctrlShift) {}

    protected abstract ChangeValues<T> getChangeValues();

    protected abstract T add(T a, T b);

    protected abstract T multiply(T a, T b);

    protected abstract T clamp(T value, T min, T max);

    protected abstract void setTextFieldRange(TextFieldWidget textField, T min, T max);

    protected abstract T getOne(boolean positive);

    /////////////////////////////////////////////////
    // *********** IMPLEMENTATION ***********//
    /////////////////////////////////////////////////
    private static final double DECIMAL_STEP = 0.01;
    private final ChangeValues<T> CHANGE_VALUES = getChangeValues();
    @Getter
    private final Supplier<T> valueSupplier;
    @Getter
    private T min = defaultMin();
    @Getter
    private T max = defaultMax();
    private final Consumer<T> onChanged;
    private TextFieldWidget textField;

    public NumberInputWidget(Supplier<T> valueSupplier, Consumer<T> onChanged) {
        this(0, 0, 100, 20, valueSupplier, onChanged);
    }

    public NumberInputWidget(Position position, Supplier<T> valueSupplier, Consumer<T> onChanged) {
        this(position, new Size(100, 20), valueSupplier, onChanged);
    }

    public NumberInputWidget(Position position, Size size, Supplier<T> valueSupplier, Consumer<T> onChanged) {
        this(position.x, position.y, size.width, size.height, valueSupplier, onChanged);
    }

    public NumberInputWidget(int x, int y, int width, int height, Supplier<T> valueSupplier, Consumer<T> onChanged) {
        super(x, y, width, height);
        this.valueSupplier = valueSupplier;
        this.onChanged = onChanged;
        buildUI();
    }

    @Override
    public void initWidget() {
        super.initWidget();
        textField.setCurrentString(toText(valueSupplier.get()));
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        buffer.writeUtf(toText(valueSupplier.get()));
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        textField.setCurrentString(buffer.readUtf());
    }

    private void buildUI() {
        Adjuster field = isIntegral() ?
                NumberField.ofLong(getSize().width, this::getLongValue, this::setLongValue, this::getLongMin, this::getLongMax).setSteps(getLongSteps()) :
                DecimalField.of(getSize().width, this::getDoubleValue, this::setDoubleValue, this::getDoubleMin, this::getDoubleMax, DECIMAL_STEP, getDecimalStepCounts());
        field.setSelfPosition(new Position(0, (getSize().height - Adjuster.HEIGHT) / 2));
        this.textField = field.getField().getInput();
        this.updateTextFieldRange();
        this.addWidget(field);
    }

    public NumberInputWidget<T> setMin(T min) {
        this.min = min;
        updateTextFieldRange();
        return this;
    }

    public NumberInputWidget<T> setMax(T max) {
        this.max = max;
        updateTextFieldRange();
        return this;
    }

    public NumberInputWidget<T> setValue(T value) {
        if (valueSupplier.get().equals(value)) return this;
        onChanged.accept(value);
        return this;
    }

    protected boolean isIntegral() {
        return true;
    }

    // ==================== 新式界面桥接 ====================
    // 新式数值输入（uipro NumberField）按 long 读写；Integer / Long 两种实现的值、上下限、步长都在 long 范围内。

    public long getLongValue() {
        return valueSupplier.get().longValue();
    }

    /** 夹到上下限后写入（先按 long 夹，再转回本类型，Integer 实现不会溢出）。 */
    public void setLongValue(long value) {
        long clamped = Math.max(min.longValue(), Math.min(max.longValue(), value));
        setValue(fromText(Long.toString(clamped)));
    }

    public long getLongMin() {
        return min.longValue();
    }

    public long getLongMax() {
        return max.longValue();
    }

    /** 四档步长：默认 / Shift / Ctrl / Ctrl+Shift。 */
    public long[] getLongSteps() {
        return new long[] { CHANGE_VALUES.regular().longValue(), CHANGE_VALUES.shift().longValue(), CHANGE_VALUES.ctrl().longValue(), CHANGE_VALUES.ctrlShift().longValue() };
    }

    protected void updateTextFieldRange() {
        this.setValue(clamp(valueSupplier.get(), min, max));
    }

    public double getDoubleValue() {
        return valueSupplier.get().doubleValue();
    }

    public void setDoubleValue(double value) {
        double clamped = Math.max(min.doubleValue(), Math.min(max.doubleValue(), value));
        setValue(fromText(BigDecimal.valueOf(clamped).toPlainString()));
    }

    public double getDoubleMin() {
        return min.doubleValue();
    }

    public double getDoubleMax() {
        return max.doubleValue();
    }

    public long[] getDecimalStepCounts() {
        return new long[] { Math.round(CHANGE_VALUES.regular().doubleValue() / DECIMAL_STEP), Math.round(CHANGE_VALUES.shift().doubleValue() / DECIMAL_STEP),
                Math.round(CHANGE_VALUES.ctrl().doubleValue() / DECIMAL_STEP), Math.round(CHANGE_VALUES.ctrlShift().doubleValue() / DECIMAL_STEP) };
    }
}
