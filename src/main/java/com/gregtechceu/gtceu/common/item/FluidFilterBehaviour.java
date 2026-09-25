package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverUIs;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public record FluidFilterBehaviour(Function<ItemStack, FluidFilter> filterCreator) implements IItemUIFactory {

    @Override
    public void onAttached(Item item) {
        IItemUIFactory.super.onAttached(item);
        FluidFilter.FILTERS.put(item, filterCreator);
    }

    @Override
    public ModularUI createUI(HeldItemUIFactory.HeldItemHolder holder, Player entityPlayer) {
        var held = holder.getHeld();
        var page = new HeldFilterPage(held, () -> CoverUIs.page().addChild(UIElement.section().addChild(FluidFilter.loadFilter(held).createConfigUI())));
        return new ModularUI(176, 166, holder, entityPlayer).widget(new MachineWindow(page));
    }
}
