package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public interface IInputLimitableMachine extends IFancyUIMachine {

    boolean isInputLimit();

    void setInputLimit(boolean isInputLimit);

    default boolean hasInputLimitConfig() {
        return true;
    }

    static List<Component> antiClogTooltips(boolean enabled) {
        if (enabled) {
            return List.of(
                    Component.translatable("gtceu.machine.anti_clog")
                            .append(Component.translatable("gtceu.machine.anti_clog.enabled").withStyle(ChatFormatting.GREEN)),
                    Component.translatable("gtceu.machine.anti_clog.enabled.0").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gtceu.machine.anti_clog.enabled.1").withStyle(ChatFormatting.GRAY));
        }
        return List.of(
                Component.translatable("gtceu.machine.anti_clog")
                        .append(Component.translatable("gtceu.machine.anti_clog.disabled").withStyle(ChatFormatting.RED)),
                Component.translatable("gtceu.machine.anti_clog.disabled.0").withStyle(ChatFormatting.GRAY));
    }

    @Override
    default void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        if (hasInputLimitConfig()) configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                WidgetIcons.ANTI_CLOG_OFF,
                WidgetIcons.ANTI_CLOG_ON,
                this::isInputLimit, (clickData, pressed) -> setInputLimit(pressed))
                .setTooltipsSupplier(IInputLimitableMachine::antiClogTooltips));
    }
}
