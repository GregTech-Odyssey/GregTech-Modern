package com.gregtechceu.gtceu.uiwidgets.inventory;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

/**
 * 小格子库存（共享物品库、共享流体库，以及其他挂在机器左侧、展开后是一小块槽位的配置项）。
 * <p>
 * 槽位用框架的标准物品槽 / 流体槽，紧排成近似正方形且每行排满（见 {@link SlotGrid#square}），最多一行
 * {@link UISizes#SLOTS_PER_ROW} 个；不再套 GTM 的深色底板。
 */
public final class SlotGridView {

    private SlotGridView() {}

    public static UIElement items(CustomItemStackHandler inventory) {
        return SlotGrid.square(inventory.getSlots(), i -> ItemSlot.of(inventory, i));
    }

    public static UIElement tanks(CustomFluidTank[] tanks) {
        return SlotGrid.square(tanks.length, i -> FluidSlot.of(tanks[i]));
    }
}
