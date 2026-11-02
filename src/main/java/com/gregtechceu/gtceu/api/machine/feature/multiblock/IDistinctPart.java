package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

public interface IDistinctPart extends IMultiPart {

    Component DISTINCT_HEADER = Component.translatable("gtceu.multiblock.universal.distinct")
            .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW));
    Component DISTINCT_YES = Component.translatable("gtceu.multiblock.universal.distinct.yes");
    Component DISTINCT_NO = Component.translatable("gtceu.multiblock.universal.distinct.no");
    List<Component> DISTINCT_TOOLTIP_YES = List.of(DISTINCT_HEADER.copy().append(DISTINCT_YES));
    List<Component> DISTINCT_TOOLTIP_NO = List.of(DISTINCT_HEADER.copy().append(DISTINCT_NO));

    boolean isDistinct();

    void setDistinct(boolean isDistinct);

    @Override
    default void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        superAttachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                WidgetIcons.DISTINCT_OFF,
                WidgetIcons.DISTINCT_ON,
                this::isDistinct, (clickData, pressed) -> setDistinct(pressed))
                .setTooltipsSupplier(pressed -> pressed ? DISTINCT_TOOLTIP_YES : DISTINCT_TOOLTIP_NO));
    }

    default void superAttachConfigurators(ConfiguratorPanel configuratorPanel) {
        IMultiPart.super.attachConfigurators(configuratorPanel);
    }
}
