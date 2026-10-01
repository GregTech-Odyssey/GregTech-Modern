package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TabsWidget;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uiwidgets.display.DetailsTab;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 带标准多方块主页的控制器：机器窗口、部件页签、"详细信息"页签、部件悬浮提示、背包让出主页滚动条位置。
 */
public interface IMultiblockFancyUIMachine extends IFancyUIMachine, IDisplayUIMachine {

    @Override
    default ModularUI createUI(Player entityPlayer) {
        return MachineWindow.createUI(this, this, entityPlayer);
    }

    @Override
    default List<IFancyUIProvider> getSubTabs() {
        return Arrays.stream(getParts()).filter(Objects::nonNull).map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    default void attachSideTabs(TabsWidget sideTabs) {
        IFancyUIMachine.super.attachSideTabs(sideTabs);
        sideTabs.attachSubTab(0, createDetailsTab());
    }

    default DetailsTab createDetailsTab() {
        return DetailsTab.display(this);
    }

    @Override
    default void attachTooltips(TooltipsPanel tooltipsPanel) {
        tooltipsPanel.attachRecipeLogics(self());
        attachPartTooltips(tooltipsPanel);
    }

    default void attachPartTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    @Override
    default int getInventoryGutter() {
        return ScrollerView.SCROLL_BAR_SPACE;
    }
}
