package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;
import com.gregtechceu.gtceu.uiwidgets.item.HeldItemPage;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public record ItemFilterBehaviour(Function<ItemStack, ItemFilter> filterCreator) implements IItemUIFactory {

    @Override
    public void onAttached(Item item) {
        IItemUIFactory.super.onAttached(item);
        ItemFilter.FILTERS.put(item, filterCreator);
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        var held = holder.getHeld();
        return HeldItemPage.create(holder, entityPlayer,
                window -> CoverUIs.page().addChild(UIElement.section().addChild(ItemFilter.loadFilter(held).createConfigUI())));
    }
}
