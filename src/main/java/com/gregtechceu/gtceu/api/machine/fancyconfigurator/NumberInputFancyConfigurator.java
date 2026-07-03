package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.widget.NumberInputWidget;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

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

    @Override
    public Widget createMainPage(FancyMachineUIWidget widget) {
        var group = new NumberInputConfigurator<>(inputWidget);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    public NumberInputFancyConfigurator<T> setMin(T min) {
        inputWidget.setMin(min);
        return this;
    }

    public NumberInputFancyConfigurator<T> setMax(T max) {
        inputWidget.setMax(max);
        return this;
    }

    private static class NumberInputConfigurator<T extends Number> extends WidgetGroup {

        public NumberInputConfigurator(NumberInputWidget<T> inputWidget) {
            super(inputWidget.getPosition(), inputWidget.getSize());
            this.addWidget(inputWidget);
        }
    }
}
