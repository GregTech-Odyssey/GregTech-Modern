package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.uipro.elements.NumberField;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.number.NumberSettingPage;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 超频次数限制的配置项：0 表示禁止超频，上限 {@link IOverclockMachine#MAX_OVERCLOCK_COUNT}。
 * <p>
 * 与时长下限（{@code gtocore} 侧的超频极限）是两个独立的限制，先到哪个按哪个停。
 */
public class OverclockCountConfigurator implements IFancyConfigurator {

    private final IOverclockMachine machine;

    public OverclockCountConfigurator(IOverclockMachine machine) {
        this.machine = machine;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtceu.gui.overclock_count.title");
    }

    @Override
    public IGuiTexture getIcon() {
        return WidgetIcons.SETTINGS;
    }

    @Override
    public Widget createConfigurator() {
        return NumberSettingPage.compact(machine::getOverclockCountLimit, count -> machine.setOverclockCountLimit((int) count), () -> 0, () -> IOverclockMachine.MAX_OVERCLOCK_COUNT, NumberField.DEFAULT_STEPS);
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(getTitle(), Component.translatable("gtceu.gui.overclock_count.tooltip"));
    }
}
