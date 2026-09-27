package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.chat.Component;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class ButtonConfigurator implements IFancyConfiguratorButton {

    @Getter
    protected IGuiTexture icon;
    protected Consumer<ClickData> onClick;
    protected List<Component> tooltips = Collections.emptyList();
    protected List<Component> busyTooltips = Collections.emptyList();
    @Nullable
    private SyncValue<Boolean> busy;

    public ButtonConfigurator(IGuiTexture texture, Consumer<ClickData> onClick) {
        this.icon = texture;
        this.onClick = onClick;
    }

    @Override
    public void onClick(ClickData clickData) {
        onClick.accept(clickData);
    }

    @Override
    public void bindSync(SyncValueHost host) {
        if (busy != null) host.add(busy);
    }

    @Override
    public boolean isBusy() {
        return busy != null && busy.getValue();
    }

    @Override
    public List<Component> getTooltips() {
        return isBusy() ? busyTooltips : tooltips;
    }

    /**
     * @return {@code this}.
     */
    public ButtonConfigurator setTooltips(final List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    /**
     * @return {@code this}.
     */
    public ButtonConfigurator setBusy(BooleanSupplier busy, List<Component> busyTooltips) {
        this.busy = SyncValue.of(busy::getAsBoolean, ByteStreamCodec.BOOLEAN_CODEC, false);
        this.busyTooltips = busyTooltips;
        return this;
    }
}
