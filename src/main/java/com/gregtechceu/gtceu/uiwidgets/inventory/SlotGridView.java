package com.gregtechceu.gtceu.uiwidgets.inventory;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import java.util.function.IntFunction;

/**
 * 小格子库存（共享物品库、共享流体库，以及其他挂在机器左侧、展开后是一小块槽位的配置项）。
 * <p>
 * 槽位用框架的标准物品槽 / 流体槽，紧排成近似正方形：列数取格数的平方根（8 格排成 4×2），最多一行
 * {@link UISizes#SLOTS_PER_ROW} 个；不再套 GTM 的深色底板。
 */
public final class SlotGridView {

    private SlotGridView() {}

    public static UIElement items(CustomItemStackHandler inventory) {
        return grid(inventory.getSlots(), i -> ItemSlot.of(inventory, i));
    }

    public static UIElement tanks(CustomFluidTank[] tanks) {
        return grid(tanks.length, i -> FluidSlot.of(tanks[i]));
    }

    /** 列数：格数的平方根（8 格为 4），1 到一行上限之间。 */
    public static int columns(int count) {
        if (count == 8) return 4;
        return Math.max(1, Math.min(UISizes.SLOTS_PER_ROW, (int) Math.ceil(Math.sqrt(count))));
    }

    /** 行数：按 {@link #columns} 排开后的行数。 */
    public static int rows(int count) {
        int columns = columns(count);
        return (count + columns - 1) / columns;
    }

    public static UIElement grid(int count, IntFunction<Widget> slot) {
        int columns = columns(count);
        var grid = UIElement.column(columns * UISizes.SLOT);
        for (int start = 0; start < count; start += columns) {
            var row = UIElement.row(UISizes.SLOT);
            for (int i = start; i < Math.min(count, start + columns); i++) row.addChild(slot.apply(i));
            grid.addChild(row);
        }
        return grid;
    }
}
