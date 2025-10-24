package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class EnergyContainerInfoList extends EnergyContainerList {

    public static final EnergyContainerInfoList EMPTY = new EnergyContainerInfoList(null, Collections.emptyList());

    @Nullable
    protected TickableSubscription updateSubs;

    protected long energyInputPerSec = 0;
    protected long energyOutputPerSec = 0;
    protected long lastEnergyInputPerSec = 0;
    protected long lastEnergyOutputPerSec = 0;
    @Nullable
    protected final MetaMachine machine;

    public EnergyContainerInfoList(@Nullable MetaMachine machine, List<IEnergyContainer> energyContainerList) {
        super(energyContainerList);
        this.machine = machine;
    }

    public void onMachineLoad() {
        if (machine == null || machine.isRemote()) return;
        updateSubs = machine.subscribeServerTick(updateSubs, this::updateTick);
    }

    public void onMachineUnLoad() {
        if (updateSubs != null) {
            updateSubs.unsubscribe();
            updateSubs = null;
        }
    }

    protected void updateTick() {
        if (machine != null && machine.getOffsetTimer() % 20 == 0) {
            lastEnergyOutputPerSec = energyOutputPerSec;
            lastEnergyInputPerSec = energyInputPerSec;
            energyOutputPerSec = 0;
            energyInputPerSec = 0;
        }
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        var change = super.changeEnergy(energyToAdd);
        if (change > 0) {
            energyInputPerSec += change;
        } else {
            energyOutputPerSec += change;
        }
        return change;
    }

    @Override
    public long getInputPerSec() {
        return lastEnergyInputPerSec;
    }

    @Override
    public long getOutputPerSec() {
        return lastEnergyOutputPerSec;
    }
}
