package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.utils.GTUtil;

import org.jetbrains.annotations.NotNull;

public interface IElectricMachine extends ITieredMachine {

    @NotNull
    IEnergyContainer getEnergyContainer();

    default int getTier() {
        return GTUtil.getTierByVoltage(getEnergyContainer().getEnergyCapacity());
    }

    default boolean useEnergy(long eu, boolean simulate) {
        if (eu < 0) {
            return generateEnergy(-eu, simulate);
        }
        if (simulate) {
            return getEnergyContainer().getEnergyStored() >= eu;
        }
        eu = -eu;
        return getEnergyContainer().changeEnergy(eu) == eu;
    }

    default boolean generateEnergy(long eu, boolean simulate) {
        var container = getEnergyContainer();
        if (container.getEnergyCapacity() - container.getEnergyStored() >= eu) {
            if (!simulate) container.changeEnergy(eu);
            return true;
        }
        return false;
    }
}
