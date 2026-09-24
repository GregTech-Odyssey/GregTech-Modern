package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

public interface IInputLimitableMachine extends IFancyUIMachine {

    boolean isInputLimit();

    void setInputLimit(boolean isInputLimit);

    default boolean hasInputLimitConfig() {
        return true;
    }

    @Override
    default void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        if (hasInputLimitConfig()) configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                WidgetIcons.INPUT_LIMIT_OFF,
                WidgetIcons.INPUT_LIMIT_ON,
                this::isInputLimit, (clickData, pressed) -> setInputLimit(pressed))
                .setTooltipsSupplier(pressed -> List.of(
                        Component.translatable("gtceu.multiblock.universal.input_limit")
                                .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW))
                                .append(Component.translatable(pressed ? "gtceu.multiblock.universal.input_limit.yes" :
                                        "gtceu.multiblock.universal.input_limit.no")))));
    }
}
