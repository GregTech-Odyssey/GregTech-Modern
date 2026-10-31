package com.gregtechceu.gtceu.uiwidgets.multiblock;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.elements.DecimalField;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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

    public static final Component SLOT_EMPTY = Component.translatable("gtceu.gui.multiblock.slot_empty");

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

    public UIElement addToggle(Component label, BooleanSupplier getter, BooleanConsumer setter, Component... tooltips) {
        var control = Switch.of(getter, value -> {
            if (value == getter.getAsBoolean()) return;
            setter.accept(value);
            machine.onChanged();
        });
        return add(Form.controlRow(ROW_HEIGHT, label, control, tooltips));
    }

    public UIElement addNumber(Component label, LongSupplier getter, LongConsumer setter, long min, long max, Component... tooltips) {
        return addNumber(label, getter, setter, () -> min, () -> max, tooltips);
    }

    public UIElement addNumber(Component label, LongSupplier getter, LongConsumer setter, LongSupplier min, LongSupplier max, Component... tooltips) {
        var field = NumberField.ofLong(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            if (value == getter.getAsLong()) return;
            setter.accept(value);
            machine.onChanged();
        }, min, max);
        return add(numberRows.add(label, field, tooltips));
    }

    public UIElement addInt(Component label, IntSupplier getter, IntConsumer setter, int min, int max, Component... tooltips) {
        return addInt(label, getter, setter, () -> min, () -> max, tooltips);
    }

    public UIElement addInt(Component label, IntSupplier getter, IntConsumer setter, IntSupplier min, IntSupplier max, Component... tooltips) {
        var field = NumberField.ofInt(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            if (value == getter.getAsInt()) return;
            setter.accept(value);
            machine.onChanged();
        }, min, max);
        return add(numberRows.add(label, field, tooltips));
    }

    public UIElement addDecimal(Component label, DoubleSupplier getter, DoubleConsumer setter, double min, double max, double step, Component... tooltips) {
        return addDecimal(label, getter, setter, () -> min, () -> max, step, tooltips);
    }

    public UIElement addDecimal(Component label, DoubleSupplier getter, DoubleConsumer setter, DoubleSupplier min, DoubleSupplier max, double step, Component... tooltips) {
        var field = DecimalField.of(UISizes.INLINE_ADJUSTER_WIDTH, getter, value -> {
            double clamped = Mth.clamp(value, min.getAsDouble(), max.getAsDouble());
            if (clamped == getter.getAsDouble()) return;
            setter.accept(clamped);
            machine.onChanged();
        }, min, max, step);
        return add(numberRows.add(label, field, tooltips));
    }

    public UIElement addChoice(Component label, int count, IntFunction<Component> option, IntSupplier getter, IntConsumer setter, Component... tooltips) {
        return add(Form.choiceRow(label, count, option, getter, index -> {
            setter.accept(index);
            machine.onChanged();
        }, tooltips));
    }

    public UIElement addSegments(Component label, int count, IntFunction<Component> option, IntSupplier getter, IntConsumer setter, Component... tooltips) {
        return add(Form.segmentRow(label, count, option, getter, index -> {
            setter.accept(index);
            machine.onChanged();
        }, tooltips));
    }

    public UIElement addCycle(Component label, int count, IntFunction<Component> option, IntSupplier getter, IntConsumer setter, Component... tooltips) {
        var labels = new String[Math.max(1, count)];
        var stepper = Stepper.of(INNER_WIDTH - 2 * UISizes.ICON_BUTTON_SIZE - 2 * UISizes.GAP, getter, index -> {
            if (index < 0 || index >= count || index == getter.getAsInt()) return;
            setter.accept(index);
            machine.onChanged();
        }, 0, Math.max(0, count - 1)).wrap().setFormatter(index -> {
            if (index < 0 || index >= count) return "";
            var optLabel = labels[index];
            if (optLabel == null) labels[index] = optLabel = option.apply(index).getString();
            return optLabel;
        });
        return add(UIElement.column(LayoutStyle.AUTO).layout(l -> l.gapAll(UISizes.GAP)).addChildren(Form.fieldLabel(label, tooltips), stepper));
    }

    public UIElement addServerButton(Component label, Component buttonText, Runnable onServerClick, Component... tooltips) {
        var button = Button.text(UISizes.BUTTON_WIDTH, buttonText).setOnServerClick(() -> {
            onServerClick.run();
            machine.onChanged();
        });
        return add(Form.controlRow(ROW_HEIGHT, label, button, tooltips));
    }

    public UIElement addServerButton(Component label, Component buttonText, Consumer<Player> onServerClick, Component... tooltips) {
        return addServerButton(label, Button.text(UISizes.BUTTON_WIDTH, buttonText), onServerClick, tooltips);
    }

    public UIElement addServerButton(Component label, Button button, Consumer<Player> onServerClick, Component... tooltips) {
        button.setOnServerClick(() -> {
            var gui = button.getGui();
            if (gui == null || gui.entityPlayer == null) return;
            onServerClick.accept(gui.entityPlayer);
            machine.onChanged();
        });
        return add(Form.controlRow(ROW_HEIGHT, label, button, tooltips));
    }

    public UIElement addClientButton(Component label, Component buttonText, Runnable onClientClick, Component... tooltips) {
        var button = Button.text(UISizes.BUTTON_WIDTH, buttonText).setOnClientClick(onClientClick);
        return add(Form.controlRow(ROW_HEIGHT, label, button, tooltips));
    }

    public UIElement addSlot(KeyInventory<AEItemKey> inventory, int index, Component label, Component... tooltips) {
        return addSlot(ItemSlot.of(inventory, index), label, contentName(inventory, index), tooltips);
    }

    public UIElement addSlot(StackInventory inventory, int index, Component label, Component... tooltips) {
        return addSlot(ItemSlot.of(inventory, index), label, contentName(inventory, index), tooltips);
    }

    public UIElement addSlot(Widget slot, Component label, @Nullable Supplier<Component> detail, Component... tooltips) {
        var text = UIElement.column(0).layout(l -> l.flex(1).gapAll(1)).addChild(Form.fieldLabel(label, tooltips));
        if (detail != null) text.addChild(TextLine.of(LayoutStyle.AUTO, detail).bindClientColor(UITheme::textSecondary));
        return add(UIElement.centeredRow(SLOT_ROW_HEIGHT).addChildren(text, slot));
    }

    public UIElement addSlots(Component label, Widget... slots) {
        var row = UIElement.row(SLOT_ROW_HEIGHT).layout(l -> l.alignCenter()).addChild(Form.label(label));
        for (var slot : slots) row.addChild(slot);
        return add(row);
    }

    public UIElement addGrid(Component label, Widget grid) {
        return add(new UIElement().layout(l -> l.row().width(LayoutStyle.AUTO).gapAll(UISizes.GAP).alignCenter().paddingVertical(1))
                .addChildren(Form.label(label), grid));
    }

    public static Supplier<Component> contentName(KeyInventory<AEItemKey> inventory, int index) {
        return MultiblockPage.cachedRef(() -> inventory.keyAt(index), ControlPanel::keyName);
    }

    public static Supplier<Component> contentName(StackInventory inventory, int index) {
        return MultiblockPage.cachedRef(() -> inventory.getStackInSlot(index), ControlPanel::stackName);
    }

    private static Component keyName(@Nullable AEItemKey key) {
        return key == null ? SLOT_EMPTY : Keys.displayStack(key).getHoverName();
    }

    private static Component stackName(ItemStack stack) {
        return stack.isEmpty() ? SLOT_EMPTY : stack.getHoverName();
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
