package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.item.tool.ToolSetting;
import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.Stepper;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiFunction;
import java.util.function.ToIntFunction;

public final class AoEBehavior implements IToolBehavior {

    public static final AoEBehavior INSTANCE = new AoEBehavior();

    private static final ToolSetting[] SETTINGS = { new AoESetting() };

    private AoEBehavior() {}

    @Override
    public ToolSetting[] getSettings() {
        return SETTINGS;
    }

    private static final class AoESetting extends ToolSetting {

        private AoESetting() {
            super("item.gtceu.tool.setting.aoe", "item.gtceu.tool.setting.aoe.detail");
        }

        @Override
        public Component value(ItemStack stack) {
            AoESymmetrical aoe = ToolHelper.getAoEDefinition(stack);
            return Component.translatable("item.gtceu.tool.setting.aoe.value",
                    2 * aoe.column() + 1, 2 * aoe.row() + 1, aoe.layer() + 1);
        }

        @Override
        public void cycle(ItemStack stack) {
            AoESymmetrical max = ToolHelper.getMaxAoEDefinition(stack);
            if (max.isZero()) return;
            AoESymmetrical current = ToolHelper.getAoEDefinition(stack);
            int steps = Math.max(max.column(), Math.max(max.row(), max.layer()));
            int next = 0;
            for (int i = 0; i < steps; i++) {
                if (preset(max, i).equals(current)) {
                    next = i + 1;
                    break;
                }
            }
            IDataComponentItem.set(stack, AoESymmetrical.KEY, preset(max, next));
        }

        private static AoESymmetrical preset(AoESymmetrical max, int level) {
            return AoESymmetrical.of(Math.min(level, max.column()), Math.min(level, max.row()), Math.min(level, max.layer()));
        }

        private static final Component COLUMNS = Component.translatable("item.gtceu.tool.aoe.columns");
        private static final Component COLUMNS_DETAIL = Component.translatable("item.gtceu.tool.aoe.columns.detail");
        private static final Component ROWS = Component.translatable("item.gtceu.tool.aoe.rows");
        private static final Component ROWS_DETAIL = Component.translatable("item.gtceu.tool.aoe.rows.detail");
        private static final Component LAYERS = Component.translatable("item.gtceu.tool.aoe.layers");
        private static final Component LAYERS_DETAIL = Component.translatable("item.gtceu.tool.aoe.layers.detail");

        @Override
        public void addRows(UIElement page, HeldItemUIFactory.HeldItemHolder holder) {
            AoESymmetrical max = ToolHelper.getMaxAoEDefinition(holder.getHeld());
            page.addChildren(
                    Form.controlRow(COLUMNS, stepper(holder, AoESymmetrical::column, AoESymmetrical::withColumn, max.column(), true), COLUMNS_DETAIL),
                    Form.controlRow(ROWS, stepper(holder, AoESymmetrical::row, AoESymmetrical::withRow, max.row(), true), ROWS_DETAIL),
                    Form.controlRow(LAYERS, stepper(holder, AoESymmetrical::layer, AoESymmetrical::withLayer, max.layer(), false), LAYERS_DETAIL));
        }

        private static Stepper stepper(HeldItemUIFactory.HeldItemHolder holder, ToIntFunction<AoESymmetrical> getter,
                                       BiFunction<AoESymmetrical, Integer, AoESymmetrical> setter, int max, boolean symmetric) {
            return Stepper.of(UISizes.VALUE_WIDTH, () -> getter.applyAsInt(ToolHelper.getAoEDefinition(holder.getHeld())),
                    value -> {
                        ItemStack stack = heldTool(holder);
                        if (stack.isEmpty()) return;
                        IDataComponentItem.set(stack, AoESymmetrical.KEY, setter.apply(ToolHelper.getAoEDefinition(stack), value));
                    }, 0, max)
                    .setFormatter(value -> Integer.toString(symmetric ? 1 + 2 * value : 1 + value));
        }
    }
}
