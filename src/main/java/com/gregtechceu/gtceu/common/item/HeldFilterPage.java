package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

record HeldFilterPage(ItemStack held, Supplier<Widget> content) implements IFancyUIProvider {

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return content.get();
    }

    @Override
    public IGuiTexture getTabIcon() {
        return new ItemStackTexture(held.copyWithCount(1));
    }

    @Override
    public Component getTitle() {
        return held.getHoverName();
    }
}
