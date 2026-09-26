package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FancySelectorConfigurator<T extends Enum<T> & EnumSelectorWidget.SelectableEnum> implements IFancyConfiguratorButton {

    private final List<T> values;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final SyncValue<Integer> selected;
    private Function<T, List<Component>> tooltip = t -> Collections.singletonList(Component.empty());
    @Nullable
    private Predicate<T> latched;

    public FancySelectorConfigurator(T[] values, Supplier<T> getter, Consumer<T> setter) {
        this.values = List.of(values);
        this.getter = getter;
        this.setter = setter;
        this.selected = SyncValue.ofInt(() -> this.values.indexOf(getter.get()), 0);
    }

    @Override
    public void bindSync(SyncValueHost host) {
        host.add(selected);
    }

    private T current() {
        int index = selected.getValue();
        return index >= 0 && index < values.size() ? values.get(index) : values.get(0);
    }

    /// 标签本身就是按钮底，直接用选项自己的图标；EnumSelectorWidget.getTexture 会再套一层原版按钮底图，放进标签成了双层底
    @Override
    public IGuiTexture getIcon() {
        return current().getIcon();
    }

    @Override
    public List<Component> getTooltips() {
        return this.tooltip.apply(current());
    }

    public FancySelectorConfigurator<T> setLatched(Predicate<T> latched) {
        this.latched = latched;
        return this;
    }

    @Override
    public boolean isPersistent() {
        return latched != null;
    }

    @Override
    public boolean isLatched() {
        return latched != null && latched.test(current());
    }

    public FancySelectorConfigurator<?> setTooltip(final Function<T, List<Component>> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void onClick(ClickData clickData) {
        if (clickData.isRemote) return;
        int index = values.indexOf(getter.get());
        setter.accept(values.get((index + 1) % values.size()));
    }
}
