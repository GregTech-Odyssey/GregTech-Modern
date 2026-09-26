package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FancySelectorConfigurator<T extends Enum<T> & EnumSelectorWidget.SelectableEnum> implements IFancyConfiguratorButton {

    private final EnumSelectorWidget<T> widget;
    private Function<T, List<Component>> tooltip = t -> Collections.singletonList(Component.empty());
    @Nullable
    private Predicate<T> latched;
    @Nullable
    private Supplier<T> source;

    public FancySelectorConfigurator(T[] values, T initialValue, Consumer<T> onChanged) {
        this.widget = new EnumSelectorWidget<>(0, 0, 20, 20, values, initialValue, onChanged);
    }

    /// 标签本身就是按钮底，直接用选项自己的图标；EnumSelectorWidget.getTexture 会再套一层原版按钮底图，放进标签成了双层底
    @Override
    public IGuiTexture getIcon() {
        return widget.getCurrentValue().getIcon();
    }

    @Override
    public List<Component> getTooltips() {
        return this.tooltip.apply(widget.getCurrentValue());
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
        return latched != null && latched.test(widget.getCurrentValue());
    }

    public FancySelectorConfigurator<T> syncFrom(Supplier<T> source) {
        this.source = source;
        return this;
    }

    private void select(int index) {
        if (index < 0 || index >= widget.values.size()) return;
        widget.selected = index;
        widget.buttonWidget.setIndex(index);
    }

    @Override
    public void detectAndSendChange(BiConsumer<Integer, Consumer<FriendlyByteBuf>> sender) {
        if (source == null) return;
        int index = widget.values.indexOf(source.get());
        if (index >= 0 && index != widget.selected) {
            select(index);
            sender.accept(0, buf -> buf.writeVarInt(index));
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buf) {
        if (id == 0) select(buf.readVarInt());
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        if (source == null) return;
        select(widget.values.indexOf(source.get()));
        buffer.writeVarInt(widget.selected);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        if (source == null) return;
        select(buffer.readVarInt());
    }

    public FancySelectorConfigurator<?> setTooltip(final Function<T, List<Component>> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public void onClick(ClickData clickData) {
        ++widget.selected;
        if (widget.selected >= widget.values.size()) {
            widget.selected = 0;
        }

        widget.buttonWidget.setIndex(widget.selected);
        if (widget.onChanged != null) {
            widget.onChanged.accept(widget.getCurrentValue());
        }
    }
}
