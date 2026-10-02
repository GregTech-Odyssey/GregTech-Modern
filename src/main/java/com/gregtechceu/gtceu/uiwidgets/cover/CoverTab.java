package com.gregtechceu.gtceu.uiwidgets.cover;

import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.uiwidgets.side.MachineSide;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class CoverTab implements IFancyUIProvider {

    private final IUICover cover;
    private final MachineSide side;
    private final Direction direction;
    private final IGuiTexture icon;

    private CoverTab(IUICover cover, MachineSide side, Direction direction) {
        this.cover = cover;
        this.side = side;
        this.direction = direction;
        this.icon = new ItemStackTexture(cover.self().getAttachItem());
    }

    public static void attach(TabsWidget tabs, MetaMachine machine) {
        var covers = machine.getCoverContainer();
        for (var side : MachineSide.values()) {
            var direction = side.toDirection(machine);
            if (covers.getCoverAtSide(direction) instanceof IUICover cover) {
                tabs.attachSubTab(new CoverTab(cover, side, direction));
            }
        }
    }

    public Direction getDirection() {
        return direction;
    }

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return new CoverPage(cover).createMainPage(widget);
    }

    @Override
    public IGuiTexture getTabIcon() {
        return icon;
    }

    @Override
    public IGuiTexture getTabCallout() {
        return side.icon();
    }

    @Override
    public Component getTitle() {
        return cover.getCoverTitle();
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(getTitle());
    }
}
