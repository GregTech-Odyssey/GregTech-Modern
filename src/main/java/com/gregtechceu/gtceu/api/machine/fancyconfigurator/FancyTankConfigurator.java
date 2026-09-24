package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.uiwidgets.inventory.SlotGridView;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class FancyTankConfigurator implements IFancyConfigurator {

    private final CustomFluidTank[] tanks;
    @Getter
    private final Component title;
    @Getter
    private List<Component> tooltips = Collections.emptyList();

    public FancyTankConfigurator(CustomFluidTank[] tanks, Component title) {
        this.tanks = tanks;
        this.title = title;
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.BUTTON_FLUID_OUTPUT;
    }

    /** 展开后的内容：标准流体槽紧排成的小网格（{@link SlotGridView}）。 */
    @Override
    public Widget createConfigurator() {
        return SlotGridView.tanks(tanks);
    }

    /**
     * @return {@code this}.
     */
    public FancyTankConfigurator setTooltips(final List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }
}
