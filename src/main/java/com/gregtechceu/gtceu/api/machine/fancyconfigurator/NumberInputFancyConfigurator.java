package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.gui.widget.NumberInputWidget;
import com.gregtechceu.gtceu.uiwidgets.number.NumberSettingPage;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A generic fancy configurator that provides a {@link NumberInputWidget} for editing numeric values.
 * <p>
 * Supports custom title, tab icon, tooltip, and min/max range through builder-style setters.
 * </p>
 *
 * @param <T> the type of number (e.g., Long, Integer)
 */
@Accessors(chain = true)
@Getter
@Setter
public class NumberInputFancyConfigurator<T extends Number> implements IFancyUIProvider {

    private Component title;
    private Supplier<IGuiTexture> tabIcon;
    private List<Component> tabTooltips;
    private List<Component> infoTooltips = List.of();

    private final NumberInputWidget<T> inputWidget;

    /**
     * Creates a new NumberInputFancyConfigurator with the default title, icon, and tooltip.
     *
     * @param inputWidget the NumberInputWidget instance to use
     */
    public NumberInputFancyConfigurator(NumberInputWidget<T> inputWidget) {
        this.inputWidget = inputWidget;
        this.title = Component.translatable("ldlib.gui.editor.configurator.count");
        this.tabIcon = () -> new ItemStackTexture(Items.PAPER);
        this.tabTooltips = Collections.singletonList(Component.translatable("gui.settings"));
    }

    @Override
    public IGuiTexture getTabIcon() {
        return tabIcon.get();
    }

    /** 数值设置页：新式数值设置（{@link NumberSettingPage}），读写经 {@link NumberInputWidget} 的桥接方法，原控件本身不再放进界面。 */
    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        return NumberSettingPage.create(title, inputWidget::getLongValue, inputWidget::setLongValue,
                inputWidget::getLongMin, inputWidget::getLongMax, inputWidget.getLongSteps());
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        if (infoTooltips.isEmpty()) return;
        tooltipsPanel.attachTooltips(new IFancyTooltip.Basic(() -> GuiTextures.INFO_ICON, () -> infoTooltips, () -> true, () -> null));
    }

    public NumberInputFancyConfigurator<T> setMin(T min) {
        inputWidget.setMin(min);
        return this;
    }

    public NumberInputFancyConfigurator<T> setMax(T max) {
        inputWidget.setMax(max);
        return this;
    }
}
