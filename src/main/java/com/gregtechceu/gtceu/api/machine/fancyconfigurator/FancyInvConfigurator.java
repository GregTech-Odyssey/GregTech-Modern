package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.inventory.SlotGridView;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

public class FancyInvConfigurator implements IFancyConfigurator {

    private final CustomItemStackHandler inventory;
    @Getter
    private final Component title;
    @Getter
    private List<Component> tooltips = Collections.emptyList();

    public FancyInvConfigurator(CustomItemStackHandler inventory, Component title) {
        this.inventory = inventory;
        this.title = title;
    }

    @Override
    public IGuiTexture getIcon() {
        return WidgetIcons.ITEMS;
    }

    /** 展开后的内容：标准物品槽紧排成的小网格（{@link SlotGridView}）。 */
    @Override
    public Widget createConfigurator() {
        return SlotGridView.items(inventory);
    }

    /**
     * @return {@code this}.
     */
    public FancyInvConfigurator setTooltips(final List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }
}
