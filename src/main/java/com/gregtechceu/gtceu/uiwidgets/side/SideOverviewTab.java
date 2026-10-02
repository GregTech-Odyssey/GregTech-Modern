package com.gregtechceu.gtceu.uiwidgets.side;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.uiwidgets.cover.CoverTab;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.List;

public final class SideOverviewTab implements IFancyUIProvider {

    private static final String TITLE = "gtceu.gui.side_overview.title";

    private final MetaMachine machine;

    private SideOverviewTab(MetaMachine machine) {
        this.machine = machine;
    }

    public static void attach(TabsWidget tabs, MetaMachine machine) {
        tabs.attachSubTab(new SideOverviewTab(machine));
        CoverTab.attach(tabs, machine);
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return SideOverview.createPage(machine);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return MachineSide.FRONT.icon();
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE);
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(getTitle());
    }
}
