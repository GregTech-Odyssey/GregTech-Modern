package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class ButtonConfigurator implements IFancyConfiguratorButton {

    @Getter
    protected IGuiTexture icon;
    protected Consumer<ClickData> onClick;
    @Getter
    protected List<Component> tooltips = Collections.emptyList();

    public ButtonConfigurator(IGuiTexture texture, Consumer<ClickData> onClick) {
        this.icon = texture;
        this.onClick = onClick;
    }

    @Override
    public void onClick(ClickData clickData) {
        onClick.accept(clickData);
    }

    /**
     * @return {@code this}.
     */
    public ButtonConfigurator setTooltips(final List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }
}
