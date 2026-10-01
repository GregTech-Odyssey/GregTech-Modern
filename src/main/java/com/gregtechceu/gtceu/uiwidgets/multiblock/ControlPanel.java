package com.gregtechceu.gtceu.uiwidgets.multiblock;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.ButtonGroup;
import com.gregtechceu.gtceu.uipro.elements.DecimalField;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 机器操作台：带铆钉的凸起板，逐行放物品槽、开关、数值、选项、按钮，行的拼法取自 {@link Form}，行间刻线。
 * 写值只在服务端执行，值没变时不调用，写完自动标脏；每个 add 方法返回整行，可再接 disabled。
 */
public final class ControlPanel {

    public static final String SLOT_EMPTY = "gtceu.gui.multiblock.slot_empty";
    private static final Component EMPTY_SLOT = Component.translatable(SLOT_EMPTY);

    private static final int PADDING = 7;
    private static final int INNER_WIDTH = UISizes.CONTENT_WIDTH - 2 * PADDING;
    private static final int ROW_HEIGHT = 16;
    private static final int SLOT_ROW_HEIGHT = UISizes.SLOT_SIZE + 2;

    private final MetaMachine machine;
    private final UIElement section;
    private final Form.InlineNumberRows numberRows = Form.inlineNumberRows(INNER_WIDTH, ROW_HEIGHT);
    private boolean empty = true;

    private ControlPanel(MetaMachine machine) {
        this.machine = machine;
        this.section = new UIElement().layout(l -> l.column().width(LayoutStyle.AUTO).paddingAll(PADDING).gapAll(1));
        section.setBackground(UITheme.DECK);
    }

    public static ControlPanel of(MetaMachine machine) {
        return new ControlPanel(machine);
    }

    public UIElement build() {
        numberRows.finish();
        return section;
    }

    public boolean isEmpty() {
        return empty;
    }

    public UIElement addToggle(String labelKey, BooleanSupplier getter, BooleanConsumer setter, String... tooltipKeys) {
        var control = Switch.of(getter, value -> {
            if (value == getter.getAsBoolean()) return;
            setter.accept(value);
            machine.onChanged();
        });
        return add(Form.controlRow(ROW_HEIGHT, labelKey, control, tooltipKeys));
    }

    public UIElement addNumber(String labelKey, LongSupplier getter, LongConsumer setter, long min, long max, String... tooltipKeys) {
        return addNumber(labelKey, getter, setter, () -> min, () -> max, tooltipKeys);
    }

    public UIElement addNumber(String labelKey, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, String... tooltipKeys) {
        var field = NumberField.ofLong(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            if (value == getter.getAsLong()) return;
            setter.accept(value);
            machine.onChanged();
        }, min, max);
        return add(numberRows.add(labelKey, field, tooltipKeys));
    }

    public UIElement addInt(String labelKey, IntSupplier getter, IntConsumer setter, int min, int max, String... tooltipKeys) {
        return addInt(labelKey, getter, setter, () -> min, () -> max, tooltipKeys);
    }

    public UIElement addInt(String labelKey, IntSupplier getter, IntConsumer setter, IntSupplier min, IntSupplier max, String... tooltipKeys) {
        var field = NumberField.ofInt(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            if (value == getter.getAsInt()) return;
            setter.accept(value);
            machine.onChanged();
        }, min, max);
        return add(numberRows.add(labelKey, field, tooltipKeys));
    }

    public UIElement addDecimal(String labelKey, DoubleSupplier getter, DoubleConsumer setter, double min, double max, double step, String... tooltipKeys) {
        return addDecimal(labelKey, getter, setter, () -> min, () -> max, step, tooltipKeys);
    }

    public UIElement addDecimal(String labelKey, DoubleSupplier getter, DoubleConsumer setter, DoubleSupplier min, DoubleSupplier max, double step, String... tooltipKeys) {
        var field = DecimalField.of(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            double clamped = Mth.clamp(value, min.getAsDouble(), max.getAsDouble());
            if (clamped == getter.getAsDouble()) return;
            setter.accept(clamped);
            machine.onChanged();
        }, min, max, step);
        return add(numberRows.add(labelKey, field, tooltipKeys));
    }

    public UIElement addChoice(String labelKey, int count, IntFunction<Component> option, IntSupplier getter, IntConsumer setter, String... tooltipKeys) {
        var group = ButtonGroup.single(count, option, getter, index -> {
            if (index < 0 || index >= count || index == getter.getAsInt()) return;
            setter.accept(index);
            machine.onChanged();
        }).horizontal();
        return add(UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(Form.fieldLabel(labelKey, tooltipKeys), group));
    }

    public UIElement addServerButton(String labelKey, String buttonKey, Runnable onServerClick, String... tooltipKeys) {
        var button = Button.translatable(UISizes.BUTTON_WIDTH, buttonKey).setOnServerClick(() -> {
            onServerClick.run();
            machine.onChanged();
        });
        return add(Form.controlRow(ROW_HEIGHT, labelKey, button, tooltipKeys));
    }

    public UIElement addClientButton(String labelKey, String buttonKey, Runnable onClientClick, String... tooltipKeys) {
        var button = Button.translatable(UISizes.BUTTON_WIDTH, buttonKey).setOnClientClick(onClientClick);
        return add(Form.controlRow(ROW_HEIGHT, labelKey, button, tooltipKeys));
    }

    public UIElement addSlot(ICustomItemStackHandler inventory, int index, String labelKey, String... tooltipKeys) {
        return addSlot(ItemSlot.of(inventory, index), labelKey, contentName(inventory, index), tooltipKeys);
    }

    public UIElement addSlot(Widget slot, String labelKey, @Nullable Supplier<Component> detail, String... tooltipKeys) {
        var text = UIElement.column(0).layout(l -> l.flex(1).gapAll(1)).addChild(Form.fieldLabel(labelKey, tooltipKeys));
        if (detail != null) text.addChild(TextLine.of(LayoutStyle.AUTO, detail).bindClientColor(UITheme::textSecondary));
        return add(UIElement.centeredRow(SLOT_ROW_HEIGHT).addChildren(text, slot));
    }

    public UIElement addSlots(String labelKey, Widget... slots) {
        var row = UIElement.row(SLOT_ROW_HEIGHT).layout(l -> l.alignCenter()).addChild(Form.label(labelKey));
        for (var slot : slots) row.addChild(slot);
        return add(row);
    }

    public UIElement addGrid(String labelKey, Widget grid) {
        return add(new UIElement().layout(l -> l.row().width(LayoutStyle.AUTO).gapAll(UISizes.GAP).alignCenter().paddingVertical(1))
                .addChildren(Form.label(labelKey), grid));
    }

    public static Supplier<Component> contentName(ICustomItemStackHandler inventory, int index) {
        return MultiblockPage.cachedRef(() -> inventory.getStackInSlot(index), ControlPanel::stackName);
    }

    private static Component stackName(ItemStack stack) {
        return stack.isEmpty() ? EMPTY_SLOT : stack.getHoverName();
    }

    public UIElement add(UIElement row) {
        divider();
        section.addChild(row);
        return row;
    }

    public Widget add(Widget row) {
        divider();
        section.addChild(row);
        return row;
    }

    private void divider() {
        if (!empty) {
            var etch = new UIElement().layout(l -> l.width(LayoutStyle.AUTO).height(2));
            etch.setBackground(UITheme.ETCH);
            section.addChild(etch);
        }
        empty = false;
    }
}
