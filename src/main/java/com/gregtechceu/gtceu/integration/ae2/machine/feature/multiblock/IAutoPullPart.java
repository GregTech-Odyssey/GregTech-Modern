package com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.GenericStack;

import java.util.List;
import java.util.function.Predicate;

public interface IAutoPullPart extends IMultiPart {

    boolean isAutoPull();

    void setAutoPull(boolean autoPull);

    void setAutoPullTest(Predicate<GenericStack> test);

    @Override
    default void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                WidgetIcons.AUTO_PULL_OFF,
                WidgetIcons.AUTO_PULL_ON,
                this::isAutoPull,
                (clickData, pressed) -> setAutoPull(pressed))
                .setTooltipsSupplier(pressed -> List.of(Component.translatable("gtceu.gui.me_bus.auto_pull_button"))));
    }
}
