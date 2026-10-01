package com.gregtechceu.gtceu.uiwidgets.display;

import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;

public final class DetailsTab implements IFancyUIProvider {

    public static final String TITLE = "gtceu.gui.details";

    private final Function<FancyMachineUIWidget, Widget> page;

    private DetailsTab(Function<FancyMachineUIWidget, Widget> page) {
        this.page = page;
    }

    public static DetailsTab of(Function<FancyMachineUIWidget, Widget> page) {
        return new DetailsTab(page);
    }

    public static DetailsTab display(IDisplayUIMachine machine) {
        return new DetailsTab(widget -> MachineDisplay.detailsPage(machine));
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return page.apply(widget);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return WidgetIcons.INFO;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE);
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable(TITLE));
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }
}
