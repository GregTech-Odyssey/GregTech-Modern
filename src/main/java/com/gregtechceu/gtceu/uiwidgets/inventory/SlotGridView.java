package com.gregtechceu.gtceu.uiwidgets.inventory;

import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.ForgeStackAdapter;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;

/**
 * 小格子库存（共享物品库、共享流体库，以及其他挂在机器左侧、展开后是一小块槽位的配置项）。
 * <p>
 * 槽位用框架的标准物品槽 / 流体槽，紧排成近似正方形且每行排满（见 {@link SlotGrid#square}），最多一行
 * {@link UISizes#SLOTS_PER_ROW} 个；不再套 GTM 的深色底板。
 */
public final class SlotGridView {

    private SlotGridView() {}

    public static UIElement items(KeyInventory<AEItemKey> inventory) {
        var adapter = new MenuItemAdapter(inventory);
        return SlotGrid.square(inventory.size(), i -> ItemSlot.of(adapter, i));
    }

    public static UIElement items(StackInventory inventory) {
        var adapter = new ForgeStackAdapter(inventory);
        return SlotGrid.square(inventory.size(), i -> ItemSlot.of(adapter, i));
    }

    public static UIElement tanks(KeyInventory<AEFluidKey> tanks) {
        var adapter = new ForgeFluidAdapter(tanks);
        return SlotGrid.square(tanks.size(), i -> FluidSlot.of(adapter, i, true, true));
    }
}
