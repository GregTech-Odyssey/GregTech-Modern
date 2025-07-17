package com.gregtechceu.gtceu.api.gui.widget;

import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

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
public class FloatInputWidget extends NumberInputWidget<Float> {

    public FloatInputWidget(Supplier<Float> valueSupplier, Consumer<Float> onChanged) {
        super(valueSupplier, onChanged);
    }

    public FloatInputWidget(Position position, Supplier<Float> valueSupplier, Consumer<Float> onChanged) {
        super(position, valueSupplier, onChanged);
    }

    public FloatInputWidget(Position position, Size size, Supplier<Float> valueSupplier, Consumer<Float> onChanged) {
        super(position, size, valueSupplier, onChanged);
    }

    public FloatInputWidget(int x, int y, int width, int height, Supplier<Float> valueSupplier, Consumer<Float> onChanged) {
        super(x, y, width, height, valueSupplier, onChanged);
    }

    @Override
    protected Float defaultMin() {
        return 0F;
    }

    @Override
    protected Float defaultMax() {
        return Float.MAX_VALUE;
    }

    @Override
    protected String toText(Float value) {
        return String.valueOf(value.floatValue());
    }

    @Override
    protected Float fromText(String value) {
        return Float.parseFloat(value);
    }

    @Override
    protected ChangeValues<Float> getChangeValues() {
        return new ChangeValues<>(1F, 8F, 64F, 512F);
    }

    @Override
    protected Float add(Float a, Float b) {
        return a + b;
    }

    @Override
    protected Float multiply(Float a, Float b) {
        return a * b;
    }

    @Override
    protected Float clamp(Float value, Float min, Float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void setTextFieldRange(TextFieldWidget textField, Float min, Float max) {
        textField.setNumbersOnly(min, max);
    }

    @Override
    protected Float getOne(boolean positive) {
        return positive ? 1F : -1F;
    }
}
