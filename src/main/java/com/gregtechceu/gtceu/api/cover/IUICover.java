package com.gregtechceu.gtceu.api.cover;

import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverPage;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface IUICover extends IUIHolder {

    default CoverBehavior self() {
        return (CoverBehavior) this;
    }

    @Override
    default boolean isInvalid() {
        return self().coverHolder.isInValid() || self().coverHolder.getCoverAtSide(self().attachedSide) != self();
    }

    @Override
    default boolean isRemote() {
        return self().coverHolder.isRemote();
    }

    @Override
    default void markAsDirty() {
        self().coverHolder.onChanged();
    }

    @Override
    default ModularUI createUI(Player entityPlayer) {
        var modularUI = new ModularUI(UISizes.WINDOW_WIDTH, UISizes.WINDOW_WIDTH, this, entityPlayer)
                .widget(new MachineWindow(new CoverPage(this)));
        modularUI.registerCloseListener(this::onUIClosed);
        return modularUI;
    }

    default void onUIClosed() {}

    default Component getCoverTitle() {
        return self().getAttachItem().getHoverName();
    }

    Widget createUIWidget();
}
