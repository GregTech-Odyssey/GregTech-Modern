package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.uipro.data.UIChannel;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface IFancyConfigurator {

    Component getTitle();

    IGuiTexture getIcon();

    Widget createConfigurator();

    default List<Component> getTooltips() {
        return List.of(getTitle());
    }

    default void bindSync(UIChannel.Host host) {}
}
