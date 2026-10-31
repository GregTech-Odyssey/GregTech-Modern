package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.GTCapability;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.elements.Switch;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.uiwidgets.number.NumberSettingPage;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CreativeComputationProviderMachine extends MetaMachine implements IFancyUIMachine, IOpticalComputationHatch, IDropSaveMachine {

    @SaveToDisk(defaultValue = "0")
    @SyncToClient
    private long maxCWUt;
    @SyncToClient
    private long lastRequestedCWUt;
    private long requestedCWUPerSec;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient
    private boolean active;
    @Nullable
    private TickableSubscription computationSubs;

    public CreativeComputationProviderMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public @Nullable <T> Object getGTCapability(Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.COMPUTATION_PROVIDER) {
            return this;
        }
        return super.getGTCapability(cap, side);
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        saveCustomPersistedData(tag, true);
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        loadCustomPersistedData(tag);
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (forDrop) {
            tag.putLong("maxCWUt", maxCWUt);
            tag.putBoolean("active", active);
        }
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.contains("maxCWUt")) {
            maxCWUt = tag.getLong("maxCWUt");
        }
        if (tag.contains("active")) {
            active = tag.getBoolean("active");
            updateComputationSubscription();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComputationSubscription();
    }

    protected void updateComputationSubscription() {
        if (active) {
            this.computationSubs = subscribeServerTick(computationSubs, this::updateComputationTick, 20);
        } else if (computationSubs != null) {
            computationSubs.unsubscribe();
            this.computationSubs = null;
            this.lastRequestedCWUt = 0;
            this.requestedCWUPerSec = 0;
        }
    }

    protected void updateComputationTick() {
        this.lastRequestedCWUt = requestedCWUPerSec / 20;
        this.requestedCWUPerSec = 0;
    }

    @Override
    public long requestCWU(long cwu, boolean simulate) {
        long requestedCWUt = active ? Math.min(cwu, maxCWUt) : 0;
        if (!simulate) {
            this.requestedCWUPerSec += requestedCWUt;
        }
        return requestedCWUt;
    }

    public void setActive(boolean active) {
        this.active = active;
        updateComputationSubscription();
        onChanged();
    }

    @Override
    public Widget createUIWidget() {
        var status = new StatusPanel();
        status.addLine("gtceu.creative.computation.average", MultiblockPage.numberText(() -> lastRequestedCWUt, "CWU/t"));
        var settings = NumberSettingPage.create(Component.literal("CWU/t"), () -> maxCWUt, value -> {
            maxCWUt = value;
            onChanged();
        }, () -> 0, () -> Long.MAX_VALUE);
        settings.addChild(Form.controlRow("gtceu.creative.activity.on", Switch.of(this::isActive, this::setActive)));
        return Form.page().addChildren(status, settings);
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }

    @Override
    public boolean isTransmitter() {
        return true;
    }

    @Override
    public boolean testCapability(@Nullable Direction side) {
        return true;
    }
}
